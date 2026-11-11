# agentspaces-commerce documents

Written for: anyone who read the README and wants the rest of the story.

The README is short by design: what this is, the quick start, and how to add it to a build. Everything else lives here, one topic per document.

| Document | Written for | What it holds |
|---|---|---|
| [Records](records.md) | Java developers who write or read mandates and orders, and anyone who builds a twin in another language | The reserved names, every field of `Mandate`, `Order`, `Cart`, and `Basis`, what each record refuses, and the golden vectors. |
| [Verification](verification.md) | developers who verify mandates, and reviewers of what the check proves | The trust map, each check of `Mandates.verify` in order, what it leaves to the caller, and the `Jose` layer. |
| [Building](building.md) | contributors who build this repository | The build commands, what the tests pin, the twins in the other languages, and the licence header rule. |
| [Releasing](releasing.md) | maintainers who publish agentspaces-commerce to Maven Central | The release order, the dry run, and the release. |
