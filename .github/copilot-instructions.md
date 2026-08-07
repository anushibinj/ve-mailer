- Always run tests at the end of any code change to make sure nothing is broken. If any test fails, fix the errors before doing anything else. If a test cannot be fixed immediately, document the issue in TODO.md and continue. The following tests are mandatory:
    - backend - mvn clean test
    - frontend - pnpm lint && pnpm build
- Always update the following files whenever code changes affect documentation, environment variables, or architecture:
    - README.md
    - .env.example
    - product-architecture-flowchart.mmd
- At the end of every change, suggest me a git commit message for that change
- Whenever implementing a code change, look in the `TODO.md` file for any relevant TODOs and try to implement those as well. If you implement a TODO, mark it as done in the TODO.md file. Do not implement TODOs that are not relevant to the code change you are making. If you are not sure whether a TODO is relevant, skip it and move on. If the TODO.md file is missing, proceed with the code change without it.
- Whenever doing a code change, follow these steps:
    1. Read product-architecture-flowchart.mmd & README.md to understand the flow of the code and how it is structured.
    2. Analyze whether both backend and frontend changes are needed.
    3. If both are needed, implement backend changes first, then frontend changes.
    4. If only one is needed, implement that one.
- When making code changes, preserve existing comments in the code. If you change code that has a comment, update the comment to reflect the new behavior. Delete comments only if they are factually inaccurate after your change.

<!-- repo-mapper:begin -->
## Understanding This Repository

This repository is documented by [repo-mapper](https://github.com/anushibinj/repo-mapper).

**BLOCKING REQUIREMENT**: Before exploring any source file, opening any directory,
or answering any question about the codebase, you MUST invoke the `understand-repo`
skill first. Do not read `.repo-mapper/README.md` or any other file directly — invoke the skill
and follow its instructions. Invoking the skill IS the required entry point;
skipping it and going straight to files is not allowed.

Use the linked files in the `.repo-mapper/` directory (surfaced by the skill) to understand the codebase structure.
Only open individual source files when you need implementation details that
the map does not cover.
<!-- repo-mapper:end -->
