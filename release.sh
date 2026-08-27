#!/usr/bin/env bash
#
# Interactive release workflow for FSCrawler.
#
# Creates an isolated git worktree, builds signed artifacts, pushes Docker
# images, publishes the ZIP to GitHub Releases, deletes the SNAPSHOT
# pre-release, and optionally sends the announcement.
#
set -euo pipefail

readonly DOCKERHUB_TAGS_URL="https://hub.docker.com/r/dadoonet/fscrawler/tags"
readonly TAG_PREFIX="fscrawler"
readonly SCRIPT_NAME="${0##*/}"
readonly RELEASE_STATE_FILE_NAME=".release"
readonly LOG_TAIL_LINES=50

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORK_DIR="${ROOT_DIR}"
RELEASE_STATE_FILE="${ROOT_DIR}/release/${RELEASE_STATE_FILE_NAME}"
START_DIR="$(pwd)"

DRY_RUN=false
LOCAL_MODE=false
ROLLBACK_ONLY=false
SKIP_TESTS=false
RELEASE_APPROVED=false
RELEASE_DEPLOYED=false
RELEASE_TRACKING=false
FAILURE_HANDLED=false
RELEASE_STATUS=""

ORIGINAL_BRANCH=""
ORIGINAL_HEAD=""
RELEASE_VERSION=""
NEXT_VERSION=""
RELEASE_BRANCH=""
RELEASE_TAG=""
PREVIOUS_TAG=""
MAVEN_EXTRA_ARGS=()
LOG_FILE=""
RELEASE_NOTES_FILE=""
WORKTREE_PATH=""
RELEASE_DATE=""
RELEASE_ZIP=""

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
                   (no Docker Hub, git push, or GitHub release)
      --rollback   Undo a local or failed release using release/${RELEASE_STATE_FILE_NAME}
      --skip-tests Add -DskipTests to Maven build commands

Modes:
  default          Interactive production release (deploy/push when confirmed)
  --dry-run        Log commands only; repository stays unchanged
  --local          Execute locally; release/${RELEASE_STATE_FILE_NAME} written as soon as changes start
  --rollback       Delete the isolated worktree, local release branch/tag; leave the
                   main checkout on its original branch

Local mode:
  - Creates an isolated git worktree under release/worktrees/<version>/
  - Commits, builds with -Prelease, tags, generates notes in that worktree
  - The main clone stays on the branch you started from (other checkouts are safe)
  - Skips Docker Hub, git push, GitHub release, and production email
  - Optionally sends a test announcement email to ANNOUNCE_TO from .env
  - release/${RELEASE_STATE_FILE_NAME} is saved early so --rollback works after a failure

Examples:
  ${SCRIPT_NAME} --help
  ${SCRIPT_NAME} --dry-run
  ${SCRIPT_NAME} --local
  ${SCRIPT_NAME} --local --skip-tests
  ${SCRIPT_NAME} --rollback

Prerequisites (default and --local):
  - Copy .env.example to .env and configure SMTP / GITHUB_REPO
  - python3, gh CLI authenticated (gh auth login)
  - Clean-ish git working tree on your integration branch
  - GPG signing configured for the Maven release profile
  - Docker Hub credentials when pushing images (or pass -Ddocker.skip)

Logs are written to release/<release-version>/release.log
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
		--skip-tests)
			SKIP_TESTS=true
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

is_skip_tests() {
	[[ "${SKIP_TESTS}" == true ]]
}

# ---------------------------------------------------------------------------
# Logging & errors
# ---------------------------------------------------------------------------

log() {
	printf '▶️  %s\n' "$*"
}

info() {
	printf 'ℹ️  %s\n' "$*"
}

warn() {
	printf '⚠️  %s\n' "$*" >&2
}

success() {
	printf '✅ %s\n' "$*"
}

fail_msg() {
	printf '❌ %s\n' "$*" >&2
}

report_failure() {
	local message=$1

	if [[ "${FAILURE_HANDLED}" == true ]]; then
		return
	fi
	FAILURE_HANDLED=true

	printf '\n' >&2
	fail_msg "${message}"

	if release_state_exists; then
		print_failure_recovery_hint
	fi

	if [[ -n "${LOG_FILE}" && -f "${LOG_FILE}" ]]; then
		printf 'ℹ️  Full log:\n' >&2
		printf '    less %q\n' "${LOG_FILE}" >&2
		printf '    tail -f %q\n' "${LOG_FILE}" >&2
		printf '%s\n' "--- Last ${LOG_TAIL_LINES} log lines (${LOG_FILE}) ---" >&2
		tail -"${LOG_TAIL_LINES}" "${LOG_FILE}" >&2 || true
		printf '%s\n' '--- end of log tail ---' >&2
	fi
}

print_failure_recovery_hint() {
	local status="${RELEASE_STATUS:-}"
	if [[ -z "${status}" && -f "${RELEASE_STATE_FILE}" ]]; then
		status="$(grep '^STATUS=' "${RELEASE_STATE_FILE}" 2>/dev/null | cut -d= -f2- || true)"
	fi

	if [[ "${status}" == "pushed" ]]; then
		printf 'ℹ️  Branch and tag are already on origin — do not expect --rollback to undo them.\n' >&2
		printf '    Continue manually, for example:\n' >&2
		printf '      python3 scripts/prepare-release-notes.py --version %s --since-tag %s\n' \
			"${RELEASE_VERSION:-<version>}" "${PREVIOUS_TAG:-<previous-tag>}" >&2
		printf '      gh release create %s --notes-file %s %s %s %s\n' \
			"${RELEASE_TAG:-<tag>}" "${RELEASE_NOTES_FILE:-release/<version>/release-notes.md}" \
			"${RELEASE_ZIP:-release/<version>/fscrawler-<version>.zip}#fscrawler-<version>.zip" \
			"${RELEASE_ZIP:-release/<version>/fscrawler-<version>.zip}.asc#fscrawler-<version>.zip.asc" \
			"${RELEASE_ZIP:-release/<version>/fscrawler-<version>.zip}.sha256#fscrawler-<version>.zip.sha256" >&2
		printf '      python3 scripts/send-announcement.py %s --subject "FSCrawler %s released"\n' \
			"${RELEASE_NOTES_FILE:-release/<version>/release-notes.md}" "${RELEASE_VERSION:-<version>}" >&2
		printf '    Or clear local state only with:\n' >&2
		printf '      %s --rollback\n' "${SCRIPT_NAME}" >&2
		return
	fi

	printf 'ℹ️  Rollback local release changes with:\n' >&2
	printf '    %s --rollback\n' "${SCRIPT_NAME}" >&2
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
# Environment & prerequisites
# ---------------------------------------------------------------------------

load_dotenv() {
	if is_dry_run || is_rollback; then
		return 0
	fi

	if [[ ! -f "${ROOT_DIR}/.env" ]]; then
		die "Missing ${ROOT_DIR}/.env — copy .env.example to .env and configure it."
	fi

	# shellcheck disable=SC1091
	set -a && source "${ROOT_DIR}/.env" && set +a
}

require_command() {
	local name=$1 hint=${2:-}

	if command -v "${name}" >/dev/null 2>&1; then
		return 0
	fi

	if [[ -n "${hint}" ]]; then
		die "Required command not found: ${name}. ${hint}"
	fi
	die "Required command not found: ${name}."
}

check_gh_auth() {
	require_command gh "Install from https://cli.github.com/ and run: gh auth login"
	if ! gh auth status >/dev/null 2>&1; then
		die "GitHub CLI is not authenticated. Run: gh auth login"
	fi

	local repo="${GITHUB_REPO:-dadoonet/fscrawler}"
	if ! gh repo view "${repo}" >/dev/null 2>&1; then
		die "Cannot access GitHub repository ${repo}. Check GITHUB_REPO in .env."
	fi
}

check_env_var() {
	local name=$1
	if [[ -z "${!name:-}" ]]; then
		die "Missing required variable in .env: ${name}"
	fi
}

check_prerequisites() {
	if is_dry_run || is_rollback; then
		return 0
	fi

	require_command python3
	check_gh_auth
}

check_release_notes_file() {
	local version=$1
	local notes_file="${ROOT_DIR}/docs/source/release/${version}.md"

	if [[ ! -f "${notes_file}" ]]; then
		die "Release notes not found: docs/source/release/${version}.md"
	fi
}

suggest_previous_tag() {
	git_cmd tag --sort=-v:refname | grep "^${TAG_PREFIX}-" | head -1 || true
}

# ---------------------------------------------------------------------------
# Release state (release/${RELEASE_STATE_FILE_NAME})
# ---------------------------------------------------------------------------

release_state_exists() {
	[[ -f "${RELEASE_STATE_FILE}" ]]
}

ensure_no_release_state() {
	release_state_exists || return 0
	die "Existing release state found (release/${RELEASE_STATE_FILE_NAME}). Run: ${SCRIPT_NAME} --rollback"
}

save_release_state() {
	local status=${1:-in_progress}
	local mode="production"
	is_local && mode="local"

	RELEASE_STATUS="${status}"
	mkdir -p "$(dirname "${RELEASE_STATE_FILE}")"
	cat >"${RELEASE_STATE_FILE}" <<EOF
ORIGINAL_BRANCH=${ORIGINAL_BRANCH}
ORIGINAL_HEAD=${ORIGINAL_HEAD:-}
RELEASE_BRANCH=${RELEASE_BRANCH}
RELEASE_TAG=${RELEASE_TAG}
RELEASE_VERSION=${RELEASE_VERSION}
NEXT_VERSION=${NEXT_VERSION}
WORKTREE_PATH=${WORKTREE_PATH:-}
RELEASE_DATE=${RELEASE_DATE:-}
RELEASE_ZIP=${RELEASE_ZIP:-}
LOG_FILE=${LOG_FILE}
MODE=${mode}
STATUS=${status}
EOF
	log "Saved release state to release/${RELEASE_STATE_FILE_NAME} (${status})"
}

load_release_state() {
	# shellcheck disable=SC1090
	source "${RELEASE_STATE_FILE}"
	if [[ -n "${WORKTREE_PATH:-}" && -d "${WORKTREE_PATH}" ]]; then
		WORK_DIR="${WORKTREE_PATH}"
	else
		WORK_DIR="${ROOT_DIR}"
	fi
	if [[ -z "${RELEASE_ZIP:-}" && -n "${RELEASE_VERSION:-}" ]]; then
		RELEASE_ZIP="${ROOT_DIR}/release/${RELEASE_VERSION}/fscrawler-${RELEASE_VERSION}.zip"
	fi
}

clear_release_state() {
	rm -f "${RELEASE_STATE_FILE}"
}

rollback_from_state_file() {
	cd "${ROOT_DIR}"

	if ! release_state_exists; then
		die "No release state file (release/${RELEASE_STATE_FILE_NAME}). Nothing to rollback."
	fi

	load_release_state

	info "Rolling back release ${RELEASE_VERSION} (${STATUS:-unknown})"
	info "  original branch: ${ORIGINAL_BRANCH}"
	info "  release branch:  ${RELEASE_BRANCH}"
	info "  release tag:     ${RELEASE_TAG}"
	if [[ -n "${WORKTREE_PATH:-}" ]]; then
		info "  worktree:        ${WORKTREE_PATH}"
	fi

	# Drop the isolated worktree first — git cannot delete a branch that is still
	# checked out in a worktree, and the main clone must stay on ORIGINAL_BRANCH.
	remove_release_worktree

	# Discard leftover filtered copies (outside target/) before switching branches.
	# git checkout keeps uncommitted files when both branches have the same blob,
	# which is how FSCRAWLER_VERSION=3.0 survived onto the SNAPSHOT branch.
	restore_filtered_working_tree

	local current_branch
	current_branch="$(git -C "${ROOT_DIR}" rev-parse --abbrev-ref HEAD)"

	if [[ "${current_branch}" != "${ORIGINAL_BRANCH}" ]]; then
		log "Checking out ${ORIGINAL_BRANCH}"
		git -C "${ROOT_DIR}" checkout -q "${ORIGINAL_BRANCH}"
	fi

	if [[ "${STATUS:-}" == "awaiting_push" && -n "${ORIGINAL_HEAD:-}" ]]; then
		log "Resetting ${ORIGINAL_BRANCH} to pre-merge commit ${ORIGINAL_HEAD}"
		git -C "${ROOT_DIR}" reset --hard "${ORIGINAL_HEAD}"
	elif [[ "${STATUS:-}" == "pushed" ]]; then
		warn "${ORIGINAL_BRANCH} and tag ${RELEASE_TAG} were already pushed to origin."
		warn "Not resetting ${ORIGINAL_BRANCH} or deleting the local tag — that would diverge from the remote."
		info "If you must remove the remote tag:"
		info "  git push origin :refs/tags/${RELEASE_TAG}"
		disable_release_tracking
		clear_release_state
		success "Release state cleared — local branch left aligned with origin."
		return
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

	# Restore again from the original branch HEAD, then re-filter so
	# distribution/test-scripts matches the SNAPSHOT POMs.
	restore_filtered_working_tree
	if [[ -z "${LOG_FILE}" ]]; then
		LOG_FILE="/dev/null"
	fi
	regenerate_filtered_resources

	disable_release_tracking
	clear_release_state
	success "Rollback complete — back on ${ORIGINAL_BRANCH}."
}

restore_filtered_working_tree() {
	log "Restoring filtered resources from HEAD (distribution/test-scripts)"
	git_cmd restore --worktree --source=HEAD -- "distribution/test-scripts" || true
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
	local prompt_suffix="[Y/n]"

	if [[ "${default}" == [Nn]* ]]; then
		prompt_suffix="[y/N]"
	fi

	if is_dry_run; then
		info "[dry-run] ${prompt} → ${default}"
		[[ "${default}" == [Yy]* ]]
		return
	fi

	while true; do
		read -r -p "${prompt} ${prompt_suffix}? " answer
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

append_maven_arg_if_missing() {
	local arg=$1
	local existing

	for existing in "${MAVEN_EXTRA_ARGS[@]}"; do
		if [[ "${existing}" == "${arg}" ]]; then
			return 0
		fi
	done

	MAVEN_EXTRA_ARGS+=("${arg}")
}

apply_maven_options() {
	if is_skip_tests; then
		append_maven_arg_if_missing "-DskipTests"
	fi

	if ((${#MAVEN_EXTRA_ARGS[@]} > 0)); then
		info "Maven options: ${MAVEN_EXTRA_ARGS[*]}"
	fi
}

# ---------------------------------------------------------------------------
# Maven & Git helpers
# ---------------------------------------------------------------------------

git_cmd() {
	git -C "${ROOT_DIR}" "$@"
}

require_release_head() {
	is_dry_run && return 0
	if [[ "${WORK_DIR}" != "${WORKTREE_PATH:-}" || -z "${WORKTREE_PATH:-}" ]]; then
		return 0
	fi
	local head
	head="$(git -C "${WORK_DIR}" rev-parse --abbrev-ref HEAD)"
	if [[ "${head}" != "${RELEASE_BRANCH}" ]]; then
		die "Release worktree HEAD is '${head}', expected '${RELEASE_BRANCH}'. Aborting to avoid mutating another checkout."
	fi
}

ensure_root_on_original_branch() {
	local head
	head="$(git -C "${ROOT_DIR}" rev-parse --abbrev-ref HEAD)"
	if [[ "${head}" != "${ORIGINAL_BRANCH}" ]]; then
		die "Main checkout is on '${head}', expected '${ORIGINAL_BRANCH}'. Another process moved the root worktree during the release."
	fi
}

remove_release_worktree() {
	if [[ -z "${WORKTREE_PATH:-}" ]]; then
		WORK_DIR="${ROOT_DIR}"
		return 0
	fi
	if is_dry_run; then
		log "[dry-run] Would remove worktree ${WORKTREE_PATH}"
		WORK_DIR="${ROOT_DIR}"
		return 0
	fi
	if git_cmd worktree list --porcelain | grep -Fxq "worktree ${WORKTREE_PATH}"; then
		log "Removing release worktree ${WORKTREE_PATH}"
		git_cmd worktree remove --force "${WORKTREE_PATH}"
	elif [[ -d "${WORKTREE_PATH}" ]]; then
		warn "Worktree path ${WORKTREE_PATH} exists but is not registered — removing directory."
		rm -rf "${WORKTREE_PATH}"
		git_cmd worktree prune
	fi
	WORK_DIR="${ROOT_DIR}"
}

copy_release_zip() {
	local src dest src_asc dest_asc dest_sha
	src="${WORK_DIR}/distribution/target/fscrawler-distribution-${RELEASE_VERSION}.zip"
	dest="${ROOT_DIR}/release/${RELEASE_VERSION}/fscrawler-${RELEASE_VERSION}.zip"
	src_asc="${src}.asc"
	dest_asc="${dest}.asc"
	dest_sha="${dest}.sha256"
	if is_dry_run; then
		log "[dry-run] Would copy ${src} -> ${dest}"
		log "[dry-run] Would copy ${src_asc} -> ${dest_asc}"
		log "[dry-run] Would write ${dest_sha}"
		return 0
	fi
	if [[ ! -f "${src}" ]]; then
		die "Release ZIP not found after build: ${src}"
	fi
	if [[ ! -f "${src_asc}" ]]; then
		die "GPG signature not found: ${src_asc} — the release profile must sign the ZIP"
	fi
	mkdir -p "$(dirname "${dest}")"
	cp "${src}" "${dest}"
	cp "${src_asc}" "${dest_asc}"
	write_sha256 "${dest}" "${dest_sha}"
	RELEASE_ZIP="${dest}"
	log "Copied release ZIP, GPG signature, and SHA-256 to ${dest}"
}

write_sha256() {
	local file=$1
	local out=$2
	local dir base
	dir="$(dirname "${file}")"
	base="$(basename "${file}")"
	if command -v sha256sum >/dev/null 2>&1; then
		( cd "${dir}" && sha256sum "${base}" >"$(basename "${out}")" )
	else
		( cd "${dir}" && shasum -a 256 "${base}" >"$(basename "${out}")" )
	fi
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
	require_release_head
	if is_dry_run; then
		log "[dry-run] mvn $*  (cwd=${WORK_DIR})"
		printf '[dry-run] mvn %s\n' "$*" >>"${LOG_FILE}"
		return 0
	fi

	log "mvn $* (cwd=${WORK_DIR})"
	if ! (
		cd "${WORK_DIR}"
		mvn "$@"
	) >>"${LOG_FILE}" 2>&1; then
		report_failure "Maven command failed: mvn $*"
		exit 1
	fi
}

git_run() {
	require_release_head
	if is_dry_run; then
		log "[dry-run] git $*  (cwd=${WORK_DIR})"
		printf '[dry-run] git %s\n' "$*" >>"${LOG_FILE}"
		return 0
	fi

	if ! git -C "${WORK_DIR}" "$@"; then
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
	log "Regenerating filtered resources (docs, contrib, distribution/test-scripts)"
	# generate-test-resources is required: distribution/test-scripts is bound to that
	# phase and lives outside target/, so mvn clean does not remove it.
	mvn_run clean generate-test-resources
}

build_release() {
	log "Building release artifacts (profile: release)"
	mvn_run clean install -Prelease "${MAVEN_EXTRA_ARGS[@]}"
	copy_release_zip
}

deploy_release() {
	log "Pushing Docker images to Docker Hub"
	mvn_run deploy -DskipTests -Prelease "${MAVEN_EXTRA_ARGS[@]}"
}

generate_announcement() {
	log "Generating release notes"
	if is_dry_run; then
		log "[dry-run] python3 scripts/prepare-release-notes.py --version ${RELEASE_VERSION} --since-tag ${PREVIOUS_TAG}"
		printf '[dry-run] prepare-release-notes.py\n' >>"${LOG_FILE}"
		return 0
	fi

	python3 "${ROOT_DIR}/scripts/prepare-release-notes.py" \
		--version "${RELEASE_VERSION}" \
		--since-tag "${PREVIOUS_TAG}" \
		--output "${RELEASE_NOTES_FILE}" >>"${LOG_FILE}" 2>&1
}

ensure_release_notes_file() {
	if is_dry_run; then
		return 0
	fi

	if [[ -f "${RELEASE_NOTES_FILE}" ]]; then
		return 0
	fi

	warn "Release notes file missing (${RELEASE_NOTES_FILE}) — regenerating."
	generate_announcement

	if [[ ! -f "${RELEASE_NOTES_FILE}" ]]; then
		die "Release notes file not found after regeneration: ${RELEASE_NOTES_FILE}"
	fi
}

send_announcement() {
	local subject="FSCrawler ${RELEASE_VERSION} released"

	if is_dry_run; then
		log "[dry-run] python3 scripts/send-announcement.py ${RELEASE_NOTES_FILE} --subject ${subject}"
		return 0
	fi

	ensure_release_notes_file

	check_env_var SMTP_HOST
	check_env_var SMTP_PORT
	check_env_var SMTP_USER
	check_env_var SMTP_PASS
	check_env_var ANNOUNCE_TO

	python3 "${ROOT_DIR}/scripts/send-announcement.py" \
		"${RELEASE_NOTES_FILE}" \
		--subject "${subject}" >>"${LOG_FILE}" 2>&1
}

delete_snapshot_prerelease() {
	local tag="${TAG_PREFIX}-${RELEASE_VERSION}-SNAPSHOT"
	if is_dry_run || is_local; then
		info "[local] Would delete GitHub pre-release ${tag}"
		return 0
	fi
	if gh release view "${tag}" >/dev/null 2>&1; then
		log "Deleting SNAPSHOT pre-release ${tag}"
		gh release delete "${tag}" --yes --cleanup-tag
		success "Deleted pre-release ${tag}."
	else
		info "No SNAPSHOT pre-release ${tag} to delete."
	fi
}

create_github_release() {
	if is_dry_run || is_local; then
		info "[local] Skipping GitHub release creation."
		return 0
	fi

	if ! confirm "Create GitHub release ${RELEASE_TAG}?" y; then
		info "Create manually when ready:"
		info "  gh release create ${RELEASE_TAG} --notes-file ${RELEASE_NOTES_FILE} ${RELEASE_ZIP}#fscrawler-${RELEASE_VERSION}.zip ${RELEASE_ZIP}.asc#fscrawler-${RELEASE_VERSION}.zip.asc ${RELEASE_ZIP}.sha256#fscrawler-${RELEASE_VERSION}.zip.sha256"
		info "  gh release delete ${TAG_PREFIX}-${RELEASE_VERSION}-SNAPSHOT --yes --cleanup-tag"
		return
	fi

	ensure_release_notes_file

	local zip="${RELEASE_ZIP:-${ROOT_DIR}/release/${RELEASE_VERSION}/fscrawler-${RELEASE_VERSION}.zip}"
	local zip_asset="${zip}#fscrawler-${RELEASE_VERSION}.zip"
	local asc_asset="${zip}.asc#fscrawler-${RELEASE_VERSION}.zip.asc"
	local sha_asset="${zip}.sha256#fscrawler-${RELEASE_VERSION}.zip.sha256"
	local repo="${GITHUB_REPO:-dadoonet/fscrawler}"
	if [[ ! -f "${zip}" || ! -f "${zip}.asc" || ! -f "${zip}.sha256" ]]; then
		die "Release ZIP, .asc, or .sha256 not found next to ${zip} — copy them after the release build, before mvn clean."
	fi

	if gh release view "${RELEASE_TAG}" >/dev/null 2>&1; then
		warn "GitHub release ${RELEASE_TAG} already exists."
		if confirm "Update release notes and attach ${zip##*/} plus signature and checksum?" y; then
			gh release edit "${RELEASE_TAG}" --draft=false --notes-file "${RELEASE_NOTES_FILE}"
			gh release upload "${RELEASE_TAG}" "${zip_asset}" "${asc_asset}" "${sha_asset}" --clobber
		else
			return
		fi
	else
		log "Creating GitHub release ${RELEASE_TAG}"
		gh release create "${RELEASE_TAG}" --notes-file "${RELEASE_NOTES_FILE}" "${zip_asset}" "${asc_asset}" "${sha_asset}"
		success "GitHub release ${RELEASE_TAG} created (ZIP, .asc, and SHA-256)."
	fi

	open_url "https://github.com/${repo}/releases/tag/${RELEASE_TAG}"
	if confirm "GitHub release looks OK — delete SNAPSHOT pre-release?" y; then
		delete_snapshot_prerelease
	else
		info "Delete later with:"
		info "  gh release delete ${TAG_PREFIX}-${RELEASE_VERSION}-SNAPSHOT --yes --cleanup-tag"
	fi
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
	WORKTREE_PATH="${WORKTREE_PATH:-${ROOT_DIR}/release/worktrees/${RELEASE_VERSION}}"
	log "Creating isolated worktree ${WORKTREE_PATH} on branch ${RELEASE_BRANCH}"
	if is_dry_run; then
		log "[dry-run] Would run: git worktree add -b ${RELEASE_BRANCH} ${WORKTREE_PATH}"
		return
	fi

	if git_cmd worktree list --porcelain | grep -Fxq "worktree ${WORKTREE_PATH}"; then
		die "Stale release worktree at ${WORKTREE_PATH}. Remove it with: git worktree remove --force ${WORKTREE_PATH}"
	fi
	if [[ -e "${WORKTREE_PATH}" ]]; then
		die "Worktree path already exists: ${WORKTREE_PATH}"
	fi
	if git_branch_exists "${RELEASE_BRANCH}"; then
		git_cmd branch -D "${RELEASE_BRANCH}"
	fi
	mkdir -p "$(dirname "${WORKTREE_PATH}")"
	git_cmd worktree add -b "${RELEASE_BRANCH}" "${WORKTREE_PATH}"
	WORK_DIR="${WORKTREE_PATH}"
	log "Main checkout stays on ${ORIGINAL_BRANCH}; Maven/git mutations run in the worktree."
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
	local current_version current_branch default_release default_next default_maven_opts maven_opts

	cd "${ROOT_DIR}"
	announce_mode
	ensure_no_release_state
	ensure_clean_enough_tree

	ORIGINAL_BRANCH="$(git -C "${ROOT_DIR}" rev-parse --abbrev-ref HEAD)"
	current_version="$(current_maven_version)"
	default_release="$(strip_snapshot "${current_version}")"
	default_next="$(suggest_next_snapshot "${default_release}")"
	default_maven_opts=""
	is_skip_tests && default_maven_opts="-DskipTests"

	info "Branch: ${ORIGINAL_BRANCH}"
	info "Current version: ${current_version}"

	RELEASE_VERSION="$(prompt_default "Release version" "${default_release}")"
	NEXT_VERSION="$(prompt_default "Next snapshot version" "${default_next}")"
	PREVIOUS_TAG="$(prompt_default "Previous release tag" "$(suggest_previous_tag)")"
	maven_opts="$(prompt_default "Extra Maven options (optional)" "${default_maven_opts}")"

	check_release_notes_file "${RELEASE_VERSION}"

	if [[ -z "${PREVIOUS_TAG}" ]]; then
		die "Previous release tag is required for GitHub generate-notes."
	fi

	if [[ -n "${maven_opts}" ]]; then
		# shellcheck disable=SC2206
		MAVEN_EXTRA_ARGS=(${maven_opts})
	fi

	apply_maven_options

	RELEASE_BRANCH="release-${RELEASE_VERSION}"
	RELEASE_TAG="${TAG_PREFIX}-${RELEASE_VERSION}"
	RELEASE_WORK_DIR="${ROOT_DIR}/release/${RELEASE_VERSION}"
	WORKTREE_PATH="${ROOT_DIR}/release/worktrees/${RELEASE_VERSION}"
	WORK_DIR="${ROOT_DIR}"
	RELEASE_DATE="$(date -u +%Y-%m-%d)"
	RELEASE_ZIP="${RELEASE_WORK_DIR}/fscrawler-${RELEASE_VERSION}.zip"
	LOG_FILE="${RELEASE_WORK_DIR}/release.log"
	RELEASE_NOTES_FILE="${RELEASE_WORK_DIR}/release-notes.md"

	if [[ "${ORIGINAL_BRANCH}" == "${RELEASE_BRANCH}" ]]; then
		die "You are already on ${RELEASE_BRANCH}. Switch to your integration branch first."
	fi

	if is_dry_run; then
		LOG_FILE="/dev/null"
		info "[dry-run] Skipping release work directory creation (${RELEASE_WORK_DIR})."
		return
	fi

	mkdir -p "${RELEASE_WORK_DIR}"
	: >"${LOG_FILE}"
	log "Logging to ${LOG_FILE}"
	info "Release work directory: ${RELEASE_WORK_DIR}"
	info "Isolated worktree:      ${WORKTREE_PATH}"
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
		info "[dry-run] Release notes preview skipped (generation not executed)."
	else
		info "Release notes preview:"
		cat "${RELEASE_NOTES_FILE}"
		echo
		info "Note: the GitHub \"What's Changed\" section is based on the remote default branch"
		info "until the tag is pushed; it is regenerated after a successful push."
	fi
	confirm "Are the release notes OK?" y || die "Release aborted — fix docs/source/release/${RELEASE_VERSION}.md and retry."
}

maybe_deploy() {
	if is_dry_run || is_local; then
		info "Skipping deployment (no Docker Hub publish)."
		return
	fi

	if confirm "Push Docker images now?" y; then
		RELEASE_APPROVED=true
		RELEASE_DEPLOYED=true
		deploy_release
	else
		info "Skipping Docker Hub push."
	fi
}

bump_development_version() {
	local dependabot_flag="--create"
	if is_local; then
		dependabot_flag="--allow-missing"
	fi

	set_project_version "${NEXT_VERSION}"
	log "Updating README versions table (${RELEASE_VERSION} → ${NEXT_VERSION})"
	if is_dry_run; then
		log "[dry-run] python3 scripts/update_readme_versions.py --released ${RELEASE_VERSION} --next ${NEXT_VERSION} --date ${RELEASE_DATE}"
		log "[dry-run] python3 scripts/update_dependabot_milestone.py --snapshot ${NEXT_VERSION}"
	else
		python3 "${ROOT_DIR}/scripts/update_readme_versions.py" \
			--readme "${WORK_DIR}/README.md" \
			--released "${RELEASE_VERSION}" \
			--next "${NEXT_VERSION}" \
			--date "${RELEASE_DATE:-$(date -u +%Y-%m-%d)}"
		python3 "${ROOT_DIR}/scripts/update_dependabot_milestone.py" \
			--file "${WORK_DIR}/.github/dependabot.yml" \
			--snapshot "${NEXT_VERSION}" \
			--github-repo "${GITHUB_REPO:-dadoonet/fscrawler}" \
			"${dependabot_flag}"
	fi
	commit_all "prepare for next development iteration"
}

verify_publications() {
	if [[ "${RELEASE_APPROVED}" != true ]]; then
		return
	fi

	info "Docker images may already be published."
	info "These prompts only decide whether to continue with the git merge and push."
	info "Answering no does not retract anything already published."

	info "Check Docker Hub tags"
	open_url "${DOCKERHUB_TAGS_URL}"
	confirm "Docker Hub looks OK — continue with git finalize?" y || RELEASE_APPROVED=false
}

finalize_local_release() {
	save_release_state "completed"
	maybe_send_local_test_announcement
	disable_release_tracking

	success "Local release rehearsal complete."
	info "Main checkout:     ${ORIGINAL_BRANCH} (unchanged)"
	info "Release worktree:  ${WORKTREE_PATH}"
	info "Release branch:    ${RELEASE_BRANCH}"
	info "Release tag:       ${RELEASE_TAG}"
	info "Log file:          ${LOG_FILE}"
	info "Release notes:     ${RELEASE_NOTES_FILE}"
	info "When finished inspecting, rollback with:"
	info "  ${SCRIPT_NAME} --rollback"
}

maybe_send_local_test_announcement() {
	if confirm "Send test announcement email to ${ANNOUNCE_TO:-ANNOUNCE_TO from .env}?" y; then
		if send_announcement; then
			success "Test announcement sent to ${ANNOUNCE_TO}."
		else
			warn "Failed to send test announcement — see ${LOG_FILE}"
		fi
		return 0
	fi

	info "Skipped test email. Preview remains in ${RELEASE_NOTES_FILE}"
	return 0
}

finalize_skipped_deploy() {
	warn "Deployment was skipped — leaving release branch and tag in place."
	save_release_state "awaiting_deploy"
	disable_release_tracking

	info "Release branch: ${RELEASE_BRANCH}"
	info "Release tag:    ${RELEASE_TAG}"
	info "Next SNAPSHOT commit is on ${RELEASE_BRANCH} (worktree ${WORKTREE_PATH})."
	info "When ready to continue, from the main checkout (${ORIGINAL_BRANCH}):"
	info "  git merge ${RELEASE_BRANCH}"
	info "  git worktree remove ${WORKTREE_PATH}"
	info "  git push origin ${ORIGINAL_BRANCH} ${RELEASE_TAG}"
	info "Or discard with:"
	info "  ${SCRIPT_NAME} --rollback"
}

finalize_failed_verification() {
	warn "Git finalize cancelled — published artifacts are NOT retracted."
	info "Docker Hub tags can usually be overwritten by deploying again with the same tag."
	info "The GitHub ZIP can be re-uploaded with gh release upload --clobber if the release already exists."
	save_release_state "verification_failed"
	disable_release_tracking

	info "Release branch: ${RELEASE_BRANCH}"
	info "Release tag:    ${RELEASE_TAG}"
	info "Next SNAPSHOT commit is on ${RELEASE_BRANCH} (worktree ${WORKTREE_PATH})."
	info "If the publications are actually fine, continue from the main checkout (${ORIGINAL_BRANCH}):"
	info "  git merge ${RELEASE_BRANCH}"
	info "  git worktree remove ${WORKTREE_PATH}"
	info "  git push origin ${ORIGINAL_BRANCH} ${RELEASE_TAG}"
	info "To discard local branch/tag only (remote artifacts stay published):"
	info "  ${SCRIPT_NAME} --rollback"
}

finalize_awaiting_push() {
	info "Release merged locally — push was skipped."
	save_release_state "awaiting_push"
	disable_release_tracking

	info "Branch: ${ORIGINAL_BRANCH}"
	info "Release tag: ${RELEASE_TAG}"
	info "When ready to publish:"
	info "  git push origin ${ORIGINAL_BRANCH} ${RELEASE_TAG}"
	info "  python3 scripts/prepare-release-notes.py --version ${RELEASE_VERSION} --since-tag ${PREVIOUS_TAG}"
	info "Then create the GitHub release, delete the SNAPSHOT pre-release, and send the announcement manually:"
	info "  gh release create ${RELEASE_TAG} --notes-file ${RELEASE_NOTES_FILE} ${RELEASE_ZIP}#fscrawler-${RELEASE_VERSION}.zip ${RELEASE_ZIP}.asc#fscrawler-${RELEASE_VERSION}.zip.asc ${RELEASE_ZIP}.sha256#fscrawler-${RELEASE_VERSION}.zip.sha256"
	info "  gh release delete ${TAG_PREFIX}-${RELEASE_VERSION}-SNAPSHOT --yes --cleanup-tag"
	info "To undo the local merge and clear release state:"
	info "  ${SCRIPT_NAME} --rollback"
}

finalize_release() {
	if is_local; then
		finalize_local_release
		return
	fi

	# Skipped deploy is not a failure: keep branch/tag for a later deploy or merge.
	if [[ "${RELEASE_DEPLOYED}" != true ]]; then
		if is_dry_run; then
			info "[dry-run] Deployment was skipped — would leave release branch/tag in place."
			return
		fi
		finalize_skipped_deploy
		return
	fi

	if [[ "${RELEASE_APPROVED}" != true ]]; then
		if is_dry_run; then
			info "[dry-run] Publication verification failed — would leave release branch/tag in place."
			return
		fi
		finalize_failed_verification
		return
	fi

	if ! is_dry_run; then
		ensure_root_on_original_branch
	fi

	log "Merging ${RELEASE_BRANCH} into ${ORIGINAL_BRANCH}"
	if ! is_dry_run; then
		ORIGINAL_HEAD="$(git -C "${ROOT_DIR}" rev-parse HEAD)"
		git_cmd merge -q "${RELEASE_BRANCH}"
		remove_release_worktree
		git_cmd branch -q -d "${RELEASE_BRANCH}"
	else
		info "[dry-run] Would merge ${RELEASE_BRANCH} into ${ORIGINAL_BRANCH} and remove worktree ${WORKTREE_PATH}"
	fi

	# Persist pre-merge HEAD before push so --rollback can undo the merge if push fails.
	if ! is_dry_run; then
		save_release_state "awaiting_push"
	fi

	if is_dry_run; then
		info "[dry-run] Skipping push and announcement."
		return
	fi

	if ! confirm "Push ${ORIGINAL_BRANCH} and tag ${RELEASE_TAG} to origin?" n; then
		finalize_awaiting_push
		return
	fi

	git_run push origin "${ORIGINAL_BRANCH}" "${RELEASE_TAG}"
	# Push succeeded — do not allow --rollback to hard-reset the merged branch anymore.
	save_release_state "pushed"
	# Tag now exists on GitHub — regenerate notes so generate-notes uses the pushed tag range.
	log "Regenerating release notes against the pushed tag"
	generate_announcement
	create_github_release
	maybe_send_announcement
	disable_release_tracking
	clear_release_state
}

maybe_send_announcement() {
	if confirm "Send the release announcement email?" n; then
		if send_announcement; then
			success "Announcement sent."
		else
			warn "Failed to send announcement — see ${LOG_FILE}"
		fi
		return 0
	fi

	info "Send manually when ready:"
	info "  python3 scripts/send-announcement.py ${RELEASE_NOTES_FILE} --subject \"FSCrawler ${RELEASE_VERSION} released\""
	return 0
}

rollback_release() {
	if is_dry_run; then
		info "[dry-run] Release not completed — local branch/tag would be cleaned up."
		return
	fi

	warn "Release was not completed."

	if confirm "Delete worktree, branch ${RELEASE_BRANCH} and tag ${RELEASE_TAG}?" y; then
		remove_release_worktree
		if git_branch_exists "${RELEASE_BRANCH}"; then
			git_cmd branch -D "${RELEASE_BRANCH}" 2>/dev/null || true
		fi
		if git_tag_exists "${RELEASE_TAG}"; then
			git_cmd tag -d "${RELEASE_TAG}" 2>/dev/null || true
		fi
		disable_release_tracking
		clear_release_state
		success "Local release branch and tag removed."
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

	load_dotenv
	check_prerequisites
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
		success "Dry-run complete — repository state unchanged."
	elif is_local; then
		success "Done (local mode — use --rollback to clean up)."
	else
		success "Done."
	fi
}

main "$@"
