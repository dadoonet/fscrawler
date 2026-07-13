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
readonly RELEASE_STATE_FILE_NAME=".release"
readonly LOCAL_TEST_EMAIL="david@pilato.fr"
readonly LOG_TAIL_LINES=50

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RELEASE_STATE_FILE="${ROOT_DIR}/${RELEASE_STATE_FILE_NAME}"
START_DIR="$(pwd)"

DRY_RUN=false
LOCAL_MODE=false
ROLLBACK_ONLY=false
RELEASE_APPROVED=false
RELEASE_TRACKING=false
FAILURE_HANDLED=false

ORIGINAL_BRANCH=""
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
  -h, --help       Show this help message and exit
  -n, --dry-run    Simulate the release without mutating git, Maven, or remotes
  -l, --local      Full local rehearsal: branch, build, sign, release notes
                   (no Maven Central, Docker Hub, git push, or public email)
      --rollback   Undo a local or failed release using ${RELEASE_STATE_FILE_NAME}

Modes:
  default          Interactive production release (deploy/push when confirmed)
  --dry-run        Log commands only; repository stays unchanged
  --local          Execute locally; ${RELEASE_STATE_FILE_NAME} written as soon as changes start
  --rollback       Delete local release branch/tag and return to the original branch

Local mode:
  - Creates the release branch, commits, builds with -Prelease, tags, generates notes
  - Skips deploy, git push, Docker Hub, and Central Portal checks
  - Optionally sends a test announcement email to ${LOCAL_TEST_EMAIL} only
  - ${RELEASE_STATE_FILE_NAME} is saved early so --rollback works after a failure

Examples:
  ${SCRIPT_NAME} --help
  ${SCRIPT_NAME} --dry-run
  ${SCRIPT_NAME} --local
  ${SCRIPT_NAME} --rollback

Prerequisites (default and --local):
  - Clean-ish git working tree on your integration branch
  - GPG signing configured for the Maven release profile
  - ~/.m2/settings.xml server id "central" for production deploy only

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
		-l | --local)
			LOCAL_MODE=true
			shift
			;;
		--rollback)
			ROLLBACK_ONLY=true
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

	if is_dry_run && is_local; then
		die "--dry-run and --local are mutually exclusive."
	fi

	if is_rollback && { is_dry_run || is_local; }; then
		die "--rollback cannot be combined with other modes."
	fi
}

is_dry_run() {
	[[ "${DRY_RUN}" == true ]]
}

is_local() {
	[[ "${LOCAL_MODE}" == true ]]
}

is_rollback() {
	[[ "${ROLLBACK_ONLY}" == true ]]
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

report_failure() {
	local message=$1

	if [[ "${FAILURE_HANDLED}" == true ]]; then
		return
	fi
	FAILURE_HANDLED=true

	printf '\n✗ %s\n' "${message}" >&2

	if release_state_exists; then
		printf 'ℹ Rollback local release changes with:\n' >&2
		printf '    %s --rollback\n' "${SCRIPT_NAME}" >&2
	fi

	if [[ -n "${LOG_FILE}" && -f "${LOG_FILE}" ]]; then
		printf '%s\n' 'ℹ Full log:' >&2
		printf '    less %q\n' "${LOG_FILE}" >&2
		printf '    tail -f %q\n' "${LOG_FILE}" >&2
		printf '%s\n' "--- Last ${LOG_TAIL_LINES} log lines (${LOG_FILE}) ---" >&2
		tail -"${LOG_TAIL_LINES}" "${LOG_FILE}" >&2 || true
		printf '%s\n' '--- end of log tail ---' >&2
	fi
}

die() {
	report_failure "$*"
	exit 1
}

handle_release_failure() {
	local exit_code=$1

	trap - ERR
	[[ "${exit_code}" -eq 0 ]] && return 0
	report_failure "Release step failed (exit ${exit_code})"
	exit "${exit_code}"
}

handle_release_interrupt() {
	trap - INT TERM
	report_failure "Release interrupted"
	exit 130
}

enable_release_tracking() {
	is_dry_run && return 0
	[[ "${RELEASE_TRACKING}" == true ]] && return 0

	RELEASE_TRACKING=true
	save_release_state "in_progress"
	trap 'handle_release_failure $?' ERR
	trap 'handle_release_interrupt' INT TERM
}

disable_release_tracking() {
	RELEASE_TRACKING=false
	trap - ERR INT TERM
}

show_log_tail() {
	if is_dry_run; then
		info "[dry-run] Build log tail skipped."
		return
	fi
	[[ -f "${LOG_FILE}" ]] && tail -7 "${LOG_FILE}"
}

announce_mode() {
	if is_dry_run; then
		warn "Dry-run mode — no git, Maven, browser, or remote changes will be made."
	elif is_local; then
		warn "Local mode — remote publish, push, and public email are disabled."
	fi
}

# ---------------------------------------------------------------------------
# Release state (${RELEASE_STATE_FILE_NAME})
# ---------------------------------------------------------------------------

release_state_exists() {
	[[ -f "${RELEASE_STATE_FILE}" ]]
}

ensure_no_release_state() {
	release_state_exists || return 0
	die "Existing release state found (${RELEASE_STATE_FILE_NAME}). Run: ${SCRIPT_NAME} --rollback"
}

save_release_state() {
	local status=${1:-in_progress}
	local mode="production"
	is_local && mode="local"

	cat >"${RELEASE_STATE_FILE}" <<EOF
ORIGINAL_BRANCH=${ORIGINAL_BRANCH}
RELEASE_BRANCH=${RELEASE_BRANCH}
RELEASE_TAG=${RELEASE_TAG}
RELEASE_VERSION=${RELEASE_VERSION}
NEXT_VERSION=${NEXT_VERSION}
LOG_FILE=${LOG_FILE}
MODE=${mode}
STATUS=${status}
EOF
	log "Saved release state to ${RELEASE_STATE_FILE_NAME} (${status})"
}

load_release_state() {
	# shellcheck disable=SC1090
	source "${RELEASE_STATE_FILE}"
}

clear_release_state() {
	rm -f "${RELEASE_STATE_FILE}"
}

rollback_from_state_file() {
	cd "${ROOT_DIR}"

	if ! release_state_exists; then
		die "No release state file (${RELEASE_STATE_FILE_NAME}). Nothing to rollback."
	fi

	load_release_state

	info "Rolling back release ${RELEASE_VERSION} (${STATUS:-unknown})"
	info "  original branch: ${ORIGINAL_BRANCH}"
	info "  release branch:  ${RELEASE_BRANCH}"
	info "  release tag:     ${RELEASE_TAG}"

	local current_branch
	current_branch="$(git -C "${ROOT_DIR}" rev-parse --abbrev-ref HEAD)"

	if [[ "${current_branch}" != "${ORIGINAL_BRANCH}" ]]; then
		log "Checking out ${ORIGINAL_BRANCH}"
		git -C "${ROOT_DIR}" checkout -q "${ORIGINAL_BRANCH}"
	fi

	if git_branch_exists "${RELEASE_BRANCH}"; then
		log "Deleting branch ${RELEASE_BRANCH}"
		git_cmd branch -D "${RELEASE_BRANCH}"
	else
		info "Release branch ${RELEASE_BRANCH} not found (already removed)."
	fi

	if git_tag_exists "${RELEASE_TAG}"; then
		log "Deleting tag ${RELEASE_TAG}"
		git_cmd tag -d "${RELEASE_TAG}"
	else
		info "Release tag ${RELEASE_TAG} not found (already removed)."
	fi

	disable_release_tracking
	clear_release_state
	info "Rollback complete — back on ${ORIGINAL_BRANCH}."
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

git_cmd() {
	git -C "${ROOT_DIR}" "$@"
}

git_ref_exists() {
	git_cmd show-ref --verify --quiet "$1"
}

git_tag_exists() {
	git_ref_exists "refs/tags/$1"
}

git_branch_exists() {
	git_ref_exists "refs/heads/$1"
}

working_tree_clean() {
	git_cmd diff-index --quiet HEAD --
}

mvn_run() {
	if is_dry_run; then
		log "[dry-run] mvn $*"
		printf '[dry-run] mvn %s\n' "$*" >>"${LOG_FILE}"
		return 0
	fi

	log "mvn $*"
	if ! mvn "$@" >>"${LOG_FILE}" 2>&1; then
		report_failure "Maven command failed: mvn $*"
		exit 1
	fi
}

git_run() {
	if is_dry_run; then
		log "[dry-run] git $*"
		printf '[dry-run] git %s\n' "$*" >>"${LOG_FILE}"
		return 0
	fi

	if ! git -C "${ROOT_DIR}" "$@"; then
		report_failure "Git command failed: git $*"
		exit 1
	fi
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
	local -a mail_args=()

	username="$(prompt_default "SMTP username" "${LOCAL_TEST_EMAIL}")"
	password="$(prompt_default "SMTP password" "")"

	if is_local; then
		mail_args=(-Dchanges.toAddresses="${LOCAL_TEST_EMAIL}")
		info "Local mode — announcement will be sent to ${LOCAL_TEST_EMAIL} only."
	fi

	mvn_run changes:announcement-mail \
		-Dchanges.username="${username}" \
		-Dchanges.password="${password}" \
		"${mail_args[@]}"
}

ensure_clean_enough_tree() {
	if is_dry_run; then
		info "[dry-run] Skipping working tree check."
		return
	fi

	if ! working_tree_clean; then
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

	if git_tag_exists "${tag}"; then
		confirm "Tag ${tag} already exists. Delete it?" y || die "Remove the tag manually: git tag -d ${tag}"
		git_run tag -d "${tag}"
	fi
}

create_release_branch() {
	log "Creating branch ${RELEASE_BRANCH}"
	if git_branch_exists "${RELEASE_BRANCH}"; then
		git_cmd branch -D "${RELEASE_BRANCH}"
	fi
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
	if is_dry_run || is_local; then
		info "[local] Reference URL: ${url}"
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
	announce_mode
	ensure_no_release_state
	ensure_clean_enough_tree

	ORIGINAL_BRANCH="$(git -C "${ROOT_DIR}" rev-parse --abbrev-ref HEAD)"
	current_version="$(current_maven_version)"
	default_release="$(strip_snapshot "${current_version}")"
	default_next="$(suggest_next_snapshot "${default_release}")"

	info "Branch: ${ORIGINAL_BRANCH}"
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

	if [[ "${ORIGINAL_BRANCH}" == "${RELEASE_BRANCH}" ]]; then
		warn "You are already on ${RELEASE_BRANCH}. Consider switching to your integration branch first."
	fi

	: >"${LOG_FILE}"
	log "Logging to ${LOG_FILE}"
}

prepare_release_branch() {
	remove_tag_if_requested "${RELEASE_TAG}"
	create_release_branch
	enable_release_tracking
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
	if is_dry_run || is_local; then
		info "Skipping deployment (no Maven Central / Docker Hub publish)."
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

finalize_local_release() {
	git_run checkout -q "${ORIGINAL_BRANCH}"
	save_release_state "completed"
	maybe_send_local_test_announcement

	info "Local release rehearsal complete."
	info "Release branch: ${RELEASE_BRANCH}"
	info "Release tag:    ${RELEASE_TAG}"
	info "Log file:       ${LOG_FILE}"
	info "When finished inspecting, rollback with:"
	info "  ${SCRIPT_NAME} --rollback"
}

maybe_send_local_test_announcement() {
	if ! confirm "Send test announcement email to ${LOCAL_TEST_EMAIL}?" n; then
		info "Skipped test email. Preview remains in target/announcement/announcement.vm"
		return
	fi

	git_run checkout -q "${RELEASE_TAG}"
	if send_announcement; then
		info "Test announcement sent to ${LOCAL_TEST_EMAIL}."
	else
		warn "Failed to send test announcement — see ${LOG_FILE}"
	fi
	git_run checkout -q "${ORIGINAL_BRANCH}"
}

finalize_release() {
	git_run checkout -q "${ORIGINAL_BRANCH}"

	if is_local; then
		finalize_local_release
		return
	fi

	if [[ "${RELEASE_APPROVED}" != true ]]; then
		rollback_release
		return
	fi

	log "Merging ${RELEASE_BRANCH} into ${ORIGINAL_BRANCH}"
	git_run merge -q "${RELEASE_BRANCH}"
	git_run branch -q -d "${RELEASE_BRANCH}"

	if is_dry_run; then
		info "[dry-run] Skipping push and announcement."
		return
	fi

	if ! confirm "Push ${ORIGINAL_BRANCH} and tag ${RELEASE_TAG} to origin?" n; then
		info "Not pushed. When ready:"
		info "  git push origin ${ORIGINAL_BRANCH} ${RELEASE_TAG}"
		return
	fi

	git_run push origin "${ORIGINAL_BRANCH}" "${RELEASE_TAG}"
	maybe_send_announcement
	disable_release_tracking
	clear_release_state
}

maybe_send_announcement() {
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
	git_run checkout -q "${ORIGINAL_BRANCH}"
}

rollback_release() {
	if is_dry_run; then
		info "[dry-run] Release not completed — local branch/tag would be cleaned up."
		return
	fi

	warn "Release was not completed."

	if confirm "Delete branch ${RELEASE_BRANCH} and tag ${RELEASE_TAG}?" y; then
	if git_branch_exists "${RELEASE_BRANCH}"; then
		git_cmd branch -D "${RELEASE_BRANCH}" 2>/dev/null || true
	fi
	if git_tag_exists "${RELEASE_TAG}"; then
		git_cmd tag -d "${RELEASE_TAG}" 2>/dev/null || true
	fi
		disable_release_tracking
		clear_release_state
		info "Local release branch and tag removed."
	else
		info "Left in place for manual inspection:"
		info "  branch: ${RELEASE_BRANCH}"
		info "  tag:    ${RELEASE_TAG}"
		info "  rollback later: ${SCRIPT_NAME} --rollback"
		save_release_state "in_progress"
	fi
}

# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

main() {
	parse_args "$@"

	if is_rollback; then
		rollback_from_state_file
		cd "${START_DIR}"
		return
	fi

	gather_inputs

	prepare_release_branch
	validate_build_and_tag
	review_announcement
	maybe_deploy
	bump_development_version
	verify_publications
	finalize_release

	cd "${START_DIR}"
	if is_dry_run; then
		info "Dry-run complete — repository state unchanged."
	elif is_local; then
		info "Done (local mode — use --rollback to clean up)."
	else
		info "Done."
	fi
}

main "$@"
