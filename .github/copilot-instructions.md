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
    1. Analyze whether both backend and frontend changes are needed.
    2. If both are needed, implement backend changes first, then frontend changes.
    3. If only one is needed, implement that one.
- When making code changes, preserve existing comments in the code. If you change code that has a comment, update the comment to reflect the new behavior. Delete comments only if they are factually inaccurate after your change.
