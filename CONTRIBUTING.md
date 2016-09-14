## Contributing to Flint

The Flint repo has only one "official" branch: master. This is the branch from which we cut and tag all releases. Contributions should start from another branch and be submitted as a pull request against master. In a nutshell:

1. Create a branch or fork the repo.
1. (Code code code...)
1. Run `sbt scalafmt`.
1. Run `sbt test`. Fix problems.
1. Submit a PR.

Pull requests should include unit test coverage where appropriate, especially if they're bug fixes.
