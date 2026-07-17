#!/usr/bin/env bash
#
# Licensed to Elasticsearch B.V. under one or more contributor
# license agreements. See the NOTICE file distributed with
# this work for additional information regarding copyright
# ownership. Elasticsearch B.V. licenses this file to you under
# the Apache License, Version 2.0 (the "License"); you may
# not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
#

set -euo pipefail

readonly PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly TMP_DIR="$(mktemp -d)"

cleanup() {
	rm -rf "${TMP_DIR}"
}
trap cleanup EXIT

fail() {
	printf 'FAIL: %s\n' "$*" >&2
	exit 1
}

write_awaiting_push_state() {
	local target_repo=$1
	local pre_merge_head=$2

	mkdir -p "${target_repo}/release"
	cat >"${target_repo}/release/.release" <<EOF
ORIGINAL_BRANCH=master
ORIGINAL_HEAD=${pre_merge_head}
RELEASE_BRANCH=release/1.0.0
RELEASE_TAG=fscrawler-1.0.0
RELEASE_VERSION=1.0.0
NEXT_VERSION=1.0.1-SNAPSHOT
LOG_FILE=
MODE=production
STATUS=awaiting_push
EOF
}

repo="${TMP_DIR}/repo"
remote="${TMP_DIR}/origin.git"

git init --bare -q "${remote}"
git init -q -b master "${repo}"
git -C "${repo}" config user.name "Release Test"
git -C "${repo}" config user.email "release-test@example.com"
cp "${PROJECT_ROOT}/release.sh" "${repo}/release.sh"
chmod +x "${repo}/release.sh"

printf 'base\n' >"${repo}/version.txt"
git -C "${repo}" add release.sh version.txt
git -C "${repo}" commit -q -m "base"
original_head="$(git -C "${repo}" rev-parse HEAD)"
git -C "${repo}" remote add origin "${remote}"
git -C "${repo}" push -q -u origin master

printf 'released\n' >"${repo}/version.txt"
git -C "${repo}" commit -q -am "release and next development version"
git -C "${repo}" tag fscrawler-1.0.0

# Simulate a non-atomic push where the branch succeeded but the tag failed.
git -C "${repo}" push -q origin master

# Local work after the failed push must not hide the published release commit.
printf 'local follow-up\n' >"${repo}/local.txt"
git -C "${repo}" add local.txt
git -C "${repo}" commit -q -m "local follow-up"
local_head="$(git -C "${repo}" rev-parse HEAD)"

write_awaiting_push_state "${repo}" "${original_head}"

if output="$(cd "${repo}" && ./release.sh --rollback 2>&1)"; then
	fail "rollback succeeded after the release branch was pushed without its tag"
fi

[[ "$(git -C "${repo}" rev-parse HEAD)" == "${local_head}" ]] ||
	fail "rollback reset the local branch behind its published remote"
git -C "${repo}" rev-parse --verify -q refs/tags/fscrawler-1.0.0 >/dev/null ||
	fail "rollback deleted the local release tag after a partial push"
[[ -f "${repo}/release/.release" ]] ||
	fail "rollback cleared the state needed to reconcile a partial push"

printf '%s\n' "${output}" | grep -q "partially published" ||
	fail "rollback did not explain why destructive cleanup was refused"

unpublished_repo="${TMP_DIR}/unpublished-repo"
unpublished_remote="${TMP_DIR}/unpublished-origin.git"

git init --bare -q "${unpublished_remote}"
git init -q -b master "${unpublished_repo}"
git -C "${unpublished_repo}" config user.name "Release Test"
git -C "${unpublished_repo}" config user.email "release-test@example.com"
cp "${PROJECT_ROOT}/release.sh" "${unpublished_repo}/release.sh"
chmod +x "${unpublished_repo}/release.sh"

printf 'base\n' >"${unpublished_repo}/version.txt"
git -C "${unpublished_repo}" add release.sh version.txt
git -C "${unpublished_repo}" commit -q -m "base"
unpublished_original_head="$(git -C "${unpublished_repo}" rev-parse HEAD)"
git -C "${unpublished_repo}" remote add origin "${unpublished_remote}"
git -C "${unpublished_repo}" push -q -u origin master

printf 'released\n' >"${unpublished_repo}/version.txt"
git -C "${unpublished_repo}" commit -q -am "release and next development version"
git -C "${unpublished_repo}" tag fscrawler-1.0.0
write_awaiting_push_state "${unpublished_repo}" "${unpublished_original_head}"

(cd "${unpublished_repo}" && ./release.sh --rollback >/dev/null 2>&1) ||
	fail "rollback failed even though no release refs were published"
[[ "$(git -C "${unpublished_repo}" rev-parse HEAD)" == "${unpublished_original_head}" ]] ||
	fail "rollback did not restore the unpublished pre-merge commit"
if git -C "${unpublished_repo}" rev-parse --verify -q refs/tags/fscrawler-1.0.0 >/dev/null; then
	fail "rollback retained an unpublished local release tag"
fi
[[ ! -f "${unpublished_repo}/release/.release" ]] ||
	fail "rollback retained state after cleaning up an unpublished release"

printf 'PASS: awaiting_push rollback handles published and unpublished releases\n'
