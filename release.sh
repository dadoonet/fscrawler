#!/usr/bin/env bash
#
# Interactive release workflow for FSCrawler.
#
# Creates a release branch, builds signed artifacts, publishes to Maven Central
# (central-publishing-maven-plugin), pushes Docker images, and optionally sends
# the release announcement email.
#
set -euo pipefail

readonly CENTRAL_DEPLOYMENTS_URL="https://central.sonatype.com/publishing/deployments"
readonly DOCKERHUB_TAGS_URL="https://hub.docker.com/r/dadoonet/fscrawler/tags"
readonly TAG_PREFIX="fscrawler"
readonly SCRIPT_NAME="${0##*/}"

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
START_DIR="$(pwd)"
DRY_RUN=false
RELEASE_APPROVED=false

RELEASE_VERSION=""
NEXT_VERSION=""
RELEASE_BRANCH=""
RELEASE_TAG=""
MAVEN_EXTRA_ARGS=()
LOG_FILE=""

# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

usage() {
	cat <<EOF
Usage: ${SCRIPT_NAME} [OPTIONS]

Interactive release workflow for FSCrawler.

Options:
  -h, --help     Show this help message and exit
  -n, --dry-run  Simulate the release without mutating git, Maven, or remotes
                 (no build, deploy, push, browser, or email)

Dry-run behaviour:
  - Prompts still appear; destructive confirmations default to safe answers
  - Deploy, push, and email are skipped automatically
  - Git branch/tag/commit and Maven commands are logged but not executed
  - Useful to validate the workflow on a throwaway branch

Examples:
  ${SCRIPT_NAME} --help
  ${SCRIPT_NAME} --dry-run
  ${SCRIPT_NAME} --dry-run    # extra Maven opts when prompted: -DskipTests -Ddocker.skip

Real release prerequisites:
  - Clean-ish git working tree on your integration branch
  - GPG signing configured for the Maven release profile
  - ~/.m2/settings.xml server id "central" (Sonatype Central token)
  - Docker Hub credentials when pushing images (or pass -Ddocker.skip)

Logs are written to /tmp/fscrawler-<release-version>.log
EOF
}

parse_args() {
	while [[ $# -gt 0 ]]; do
		case "$1" in
		-h | --help)
			usage
			exit 0
			;;
		-n | --dry-run)
			DRY_RUN=true
			shift
			;;
		--)
			shift
			break
			;;
		-*)
			die "Unknown option: $1 (try --help)"
			;;
		*)
			die "Unexpected argument: $1 (try --help)"
			;;
		esac
	done
}

is_dry_run() {
	[[ "${DRY_RUN}" == true ]]
}

# ---------------------------------------------------------------------------
# Logging & errors
# ---------------------------------------------------------------------------

log() {
	printf '▸ %s\n' "$*"
}

info() {
	printf 'ℹ %s\n' "$*"
}

warn() {
	printf '⚠ %s\n' "$*" >&2
}

die() {
	printf '✗ %s\n' "$*" >&2
	if [[ -n "${LOG_FILE}" && -f "${LOG_FILE}" ]]; then
		printf '\nLast 20 log lines (%s):\n' "${LOG_FILE}" >&2
		tail -20 "${LOG_FILE}" >&2 || true
	fi
	exit 1
}

show_log_tail() {
	if is_dry_run; then
		info "[dry-run] Build log tail skipped."
		return
	fi
	[[ -f "${LOG_FILE}" ]] && tail -7 "${LOG_FILE}"
}

announce_dry_run_mode() {
	is_dry_run || return 0
	warn "Dry-run mode enabled — no git, Maven, browser, or remote changes will be made."
}

# ---------------------------------------------------------------------------
# Prompts
# ---------------------------------------------------------------------------

prompt_default() {
	local prompt=$1 default=${2:-}
	local value

	read -r -p "${prompt} [${default}]: " value
	printf '%s' "${value:-${default}}"
}

confirm() {
	local prompt=$1
	local default=${2:-y}
	local answer

	if is_dry_run; then
		info "[dry-run] ${prompt} → ${default}"
		[[ "${default}" == [Yy]* ]]
		return
	fi

	while true; do
		read -r -p "${prompt} [Y/n]? " answer
		answer=${answer:-${default}}
		case "${answer}" in
		[Yy]*) return 0 ;;
		[Nn]*) return 1 ;;
		*) warn "Please answer yes or no." ;;
		esac
	done
}

# ---------------------------------------------------------------------------
# Version helpers
# ---------------------------------------------------------------------------

current_maven_version() {
	mvn -q help:evaluate -Dexpression=project.version -DforceStdout
}

strip_snapshot() {
	printf '%s' "${1%-SNAPSHOT}"
}

increment_version() {
	local version=$1
	local -a parts=(${version//./ })
	local carry=1 new len i

	for ((i = ${#parts[@]} - 1; i >= 0; i -= 1)); do
		len=${#parts[i]}
		new=$((parts[i] + carry))
		if ((${#new} > len)); then
			carry=1
		else
			carry=0
		fi
		if ((i > 0)); then
			parts[i]=${new: -len}
		else
			parts[i]=${new}
		fi
	done

	local joined="${parts[*]}"
	printf '%s' "${joined// /.}"
}

suggest_next_snapshot() {
	printf '%s-SNAPSHOT' "$(increment_version "$1")"
}

# ---------------------------------------------------------------------------
# Maven & Git helpers
# ---------------------------------------------------------------------------

mvn_run() {
	if is_dry_run; then
		log "[dry-run] mvn $*"
		printf '[dry-run] mvn %s\n' "$*" >>"${LOG_FILE}"
		return 0
	fi

	log "mvn $*"
	mvn "$@" >>"${LOG_FILE}" 2>&1
}

git_run() {
	if is_dry_run; then
		log "[dry-run] git $*"
		printf '[dry-run] git %s\n' "$*" >>"${LOG_FILE}"
		return 0
	fi

	git -C "${ROOT_DIR}" "$@"
}

set_project_version() {
	local version=$1
	log "Setting Maven version to ${version}"
	mvn_run versions:set -DnewVersion="${version}"
	mvn_run versions:commit
	regenerate_filtered_resources
}

regenerate_filtered_resources() {
	log "Regenerating filtered documentation resources"
	mvn_run clean process-resources
}

build_release() {
	log "Building release artifacts (profile: release)"
	mvn_run clean install -Prelease "${MAVEN_EXTRA_ARGS[@]}"
}

deploy_release() {
	log "Deploying artifacts to Maven Central"
	mvn_run deploy -DskipTests -Prelease
}

generate_announcement() {
	log "Generating release announcement"
	mvn_run changes:announcement-generate
}

send_announcement() {
	local username password
	username="$(prompt_default "SMTP username" "david@pilato.fr")"
	password="$(prompt_default "SMTP password" "")"
	mvn_run changes:announcement-mail \
		-Dchanges.username="${username}" \
		-Dchanges.password="${password}"
}

ensure_clean_enough_tree() {
	if is_dry_run; then
		info "[dry-run] Skipping working tree check."
		return
	fi

	if ! git_run diff-index --quiet HEAD --; then
		warn "Working tree has uncommitted changes."
		confirm "Continue anyway?" y || die "Aborting — commit or stash your changes first."
	fi
}

remove_tag_if_requested() {
	local tag=$1
	if is_dry_run; then
		log "[dry-run] Would remove existing tag ${tag} if present."
		return
	fi

	if git_run show-ref --verify --quiet "refs/tags/${tag}"; then
		confirm "Tag ${tag} already exists. Delete it?" y || die "Remove the tag manually: git tag -d ${tag}"
		git_run tag -d "${tag}"
	fi
}

create_release_branch() {
	log "Creating branch ${RELEASE_BRANCH}"
	git_run branch -D "${RELEASE_BRANCH}" 2>/dev/null || true
	git_run checkout -q -b "${RELEASE_BRANCH}"
}

commit_all() {
	local message=$1
	git_run commit -q -a -m "${message}"
}

create_release_tag() {
	log "Tagging ${RELEASE_TAG}"
	git_run tag -a "${RELEASE_TAG}" -m "Release FSCrawler version ${RELEASE_VERSION}"
}

open_url() {
	local url=$1
	if is_dry_run; then
		info "[dry-run] Would open ${url}"
		return
	fi

	if command -v open >/dev/null 2>&1; then
		open "${url}"
	elif command -v xdg-open >/dev/null 2>&1; then
		xdg-open "${url}"
	else
		info "Open in your browser: ${url}"
	fi
}

# ---------------------------------------------------------------------------
# Release phases
# ---------------------------------------------------------------------------

gather_inputs() {
	local current_version current_branch default_release default_next maven_opts

	cd "${ROOT_DIR}"
	announce_dry_run_mode
	ensure_clean_enough_tree

	current_branch="$(git -C "${ROOT_DIR}" rev-parse --abbrev-ref HEAD)"
	current_version="$(current_maven_version)"
	default_release="$(strip_snapshot "${current_version}")"
	default_next="$(suggest_next_snapshot "${default_release}")"

	info "Branch: ${current_branch}"
	info "Current version: ${current_version}"

	RELEASE_VERSION="$(prompt_default "Release version" "${default_release}")"
	NEXT_VERSION="$(prompt_default "Next snapshot version" "${default_next}")"
	maven_opts="$(prompt_default "Extra Maven options (optional)" "")"

	if [[ -n "${maven_opts}" ]]; then
		# shellcheck disable=SC2206
		MAVEN_EXTRA_ARGS=(${maven_opts})
	fi

	RELEASE_BRANCH="release-${RELEASE_VERSION}"
	RELEASE_TAG="${TAG_PREFIX}-${RELEASE_VERSION}"
	LOG_FILE="/tmp/fscrawler-${RELEASE_VERSION}.log"

	if [[ "${current_branch}" == "${RELEASE_BRANCH}" ]]; then
		warn "You are already on ${RELEASE_BRANCH}. Consider switching to your integration branch first."
	fi

	: >"${LOG_FILE}"
	log "Logging to ${LOG_FILE}"
}

prepare_release_branch() {
	remove_tag_if_requested "${RELEASE_TAG}"
	create_release_branch
	set_project_version "${RELEASE_VERSION}"
	commit_all "prepare release ${RELEASE_TAG}"
}

validate_build_and_tag() {
	build_release
	show_log_tail
	create_release_tag
}

review_announcement() {
	generate_announcement
	if is_dry_run; then
		info "[dry-run] Announcement preview skipped (generation not executed)."
	else
		info "Announcement preview:"
		cat "${ROOT_DIR}/target/announcement/announcement.vm"
		echo
	fi
	confirm "Is the announcement message OK?" y || die "Release aborted — fix the announcement and retry."
}

maybe_deploy() {
	if is_dry_run; then
		info "[dry-run] Skipping deployment."
		return
	fi

	if confirm "Deploy artifacts now?" y; then
		RELEASE_APPROVED=true
		deploy_release
	else
		info "Skipping deployment."
	fi
}

bump_development_version() {
	set_project_version "${NEXT_VERSION}"
	commit_all "prepare for next development iteration"
}

verify_publications() {
	if [[ "${RELEASE_APPROVED}" != true ]]; then
		return
	fi

	info "Check Maven Central deployment status"
	open_url "${CENTRAL_DEPLOYMENTS_URL}"
	confirm "Is the Maven Central deployment OK?" y || RELEASE_APPROVED=false

	if [[ "${RELEASE_APPROVED}" != true ]]; then
		warn "Maven Central deployment rejected."
		return
	fi

	info "Check Docker Hub tags"
	open_url "${DOCKERHUB_TAGS_URL}"
	confirm "Are the Docker images OK?" y || RELEASE_APPROVED=false
}

finalize_release() {
	local original_branch=$1

	git_run checkout -q "${original_branch}"

	if [[ "${RELEASE_APPROVED}" != true ]]; then
		rollback_release
		return
	fi

	log "Merging ${RELEASE_BRANCH} into ${original_branch}"
	git_run merge -q "${RELEASE_BRANCH}"
	git_run branch -q -d "${RELEASE_BRANCH}"

	if is_dry_run; then
		info "[dry-run] Skipping push and announcement."
		return
	fi

	if ! confirm "Push ${original_branch} and tag ${RELEASE_TAG} to origin?" n; then
		info "Not pushed. When ready:"
		info "  git push origin ${original_branch} ${RELEASE_TAG}"
		return
	fi

	git_run push origin "${original_branch}" "${RELEASE_TAG}"
	maybe_send_announcement "${original_branch}"
}

maybe_send_announcement() {
	local original_branch=$1

	if ! confirm "Send the release announcement email?" n; then
		info "Send manually from the tag checkout:"
		info "  git checkout ${RELEASE_TAG}"
		info "  mvn changes:announcement-mail"
		return
	fi

	git_run checkout -q "${RELEASE_TAG}"
	if send_announcement; then
		info "Announcement sent."
	else
		warn "Failed to send announcement — see ${LOG_FILE}"
	fi
	git_run checkout -q "${original_branch}"
}

rollback_release() {
	if is_dry_run; then
		info "[dry-run] Release not completed — local branch/tag would be cleaned up."
		return
	fi

	warn "Release was not completed."

	if confirm "Delete branch ${RELEASE_BRANCH} and tag ${RELEASE_TAG}?" y; then
		git_run branch -D "${RELEASE_BRANCH}" 2>/dev/null || true
		git_run tag -d "${RELEASE_TAG}" 2>/dev/null || true
		info "Local release branch and tag removed."
	else
		info "Left in place for manual inspection:"
		info "  branch: ${RELEASE_BRANCH}"
		info "  tag:    ${RELEASE_TAG}"
	fi
}

# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

main() {
	local original_branch

	parse_args "$@"
	gather_inputs
	original_branch="$(git -C "${ROOT_DIR}" rev-parse --abbrev-ref HEAD)"

	prepare_release_branch
	validate_build_and_tag
	review_announcement
	maybe_deploy
	bump_development_version
	verify_publications
	finalize_release "${original_branch}"

	cd "${START_DIR}"
	if is_dry_run; then
		info "Dry-run complete — repository state unchanged."
	else
		info "Done."
	fi
}

main "$@"
