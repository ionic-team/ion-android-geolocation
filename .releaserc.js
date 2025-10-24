module.exports = {
  tagFormat: '${version}',
  branches: [
    "main",
    "ci/RMET-3995/automate-releases",
  ],
  plugins: [
    "@semantic-release/commit-analyzer",
    "@semantic-release/release-notes-generator",
    [
      "@semantic-release/changelog",
      {
        changelogFile: "CHANGELOG.md",
      },
    ],
    [
      "@semantic-release/exec",
      {
        // Step 1: bump version in pom.xml
        prepareCmd: `
          echo "Updating pom.xml version to \${nextRelease.version}"
          sed -i'' -e 's#<version>.*</version>#<version>\${nextRelease.version}</version>#' pom.xml
        `,
        // Step 2: trigger publish workflow after release is done
        successCmd: `
          echo "Triggering publish-android.yml workflow..."
          curl -X POST \
            -H "Accept: application/vnd.github+json" \
            -H "Authorization: Bearer $GITHUB_TOKEN" \
            https://api.github.com/repos/ionic-team/ion-android-geolocation/actions/workflows/publish-android.yml/dispatches \
            -d '{"ref": "main", "inputs": {"version": "\${nextRelease.version}"}}'
        `,
      },
    ],
    [
      "@semantic-release/git",
      {
        assets: ["CHANGELOG.md", "pom.xml"],
        message: "chore(release): ${nextRelease.version} [skip ci]\n\n${nextRelease.notes}",
      },
    ],
    "@semantic-release/github",
  ],
};
