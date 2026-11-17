#!/usr/bin/env bash
set -euo pipefail

: "${PAGES_DEPLOY_KEY:?Missing Pages deploy key}"
dist=$(realpath "${1:?Pass the distribution directory}")
version=$(cat "$dist/version-code.txt")
[[ "$version" =~ ^[0-9]+$ ]]

publish_dir=$(mktemp -d)
trap 'rm -rf "$publish_dir"' EXIT
umask 077
printf '%s\n' "$PAGES_DEPLOY_KEY" > "$publish_dir/deploy-key"
curl --fail --silent --show-error https://api.github.com/meta |
    jq -er '.ssh_keys[] | select(startswith("ssh-ed25519 ")) | "github.com " + .' > "$publish_dir/known_hosts"
export GIT_SSH_COMMAND="ssh -i $publish_dir/deploy-key -o IdentitiesOnly=yes -o StrictHostKeyChecking=yes -o UserKnownHostsFile=$publish_dir/known_hosts"

repo=git@github.com:RealPlasticCheese/realplasticcheese.github.io.git
git clone --depth 1 --branch main "$repo" "$publish_dir/site"
cd "$publish_dir/site"
git config user.name 'Endoscope Viewer CI'
git config user.email '41898282+github-actions[bot]@users.noreply.github.com'

destination=endoscopeviewer
for attempt in 1 2 3; do
    git fetch --depth 1 origin main
    git reset --hard FETCH_HEAD
    previous=0
    if [[ -f "$destination/version-code.txt" ]]; then
        previous=$(cat "$destination/version-code.txt")
        [[ "$previous" =~ ^[0-9]+$ ]]
    fi
    if (( previous >= version )); then
        echo "Pages already has version code $previous."
        exit 0
    fi

    mkdir -p "$destination"
    cp "$dist"/{endoscope-viewer.apk,endoscope-viewer.apk.sha256,version-code.txt} "$destination/"
    git add "$destination"/{endoscope-viewer.apk,endoscope-viewer.apk.sha256,version-code.txt}
    git commit -m "Publish Endoscope Viewer APK ($version)"
    if git push origin HEAD:main; then
        exit 0
    fi
    sleep "$attempt"
done

echo 'Could not update the Pages repository.' >&2
exit 1
