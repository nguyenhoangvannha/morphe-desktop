import { appendFileSync } from 'node:fs';
import semanticRelease from 'semantic-release';

// Use the API result so the native jobs only package a version actually published
// by this run, including prereleases. A successful no-op must not reuse an old tag.
const result = await semanticRelease();
if (result && process.env.GITHUB_OUTPUT) {
  appendFileSync(process.env.GITHUB_OUTPUT, `tag=${result.nextRelease.gitTag}\n`);
}
