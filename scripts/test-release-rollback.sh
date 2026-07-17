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
release_head="$(git -C "${repo}" rev-parse HEAD)"
git -C "${repo}" tag fscrawler-1.0.0

# Simulate a non-atomic push where the branch succeeded but the tag failed.
git -C "${repo}" push -q origin master

mkdir -p "${repo}/release"
cat >"${repo}/release/.release" <<EOF
ORIGINAL_BRANCH=master
ORIGINAL_HEAD=${original_head}
RELEASE_BRANCH=release/1.0.0
RELEASE_TAG=fscrawler-1.0.0
RELEASE_VERSION=1.0.0
NEXT_VERSION=1.0.1-SNAPSHOT
LOG_FILE=
MODE=production
STATUS=awaiting_push
EOF

if output="$(cd "${repo}" && ./release.sh --rollback 2>&1)"; then
	fail "rollback succeeded after the release branch was pushed without its tag"
fi

[[ "$(git -C "${repo}" rev-parse HEAD)" == "${release_head}" ]] ||
	fail "rollback reset the local branch behind its published remote"
git -C "${repo}" rev-parse --verify -q refs/tags/fscrawler-1.0.0 >/dev/null ||
	fail "rollback deleted the local release tag after a partial push"
[[ -f "${repo}/release/.release" ]] ||
	fail "rollback cleared the state needed to reconcile a partial push"

printf '%s\n' "${output}" | grep -q "partially published" ||
	fail "rollback did not explain why destructive cleanup was refused"

printf 'PASS: awaiting_push rollback preserves partially published releases\n'
