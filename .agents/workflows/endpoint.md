---
description: Add or change a REST endpoint across Controller, DTO, Service, DAO with one unit test, then hand the API contract to the UI.
---

# /endpoint: Backend endpoint

The user's request follows the command.
Layer order (touch only what is needed):
1. Controller: hcms-web/src/main/java/edu/vit/hcms/web/controller/<Name>Controller.java
2. DTO: hcms-web/src/main/java/edu/vit/hcms/web/dto/
3. Service: hcms-service/.../complaint/ComplaintManager.java (or the relevant service)
4. DAO: hcms-persistence/.../dao/ ONLY if a new query is needed. If you touch a DAO, read docs/LESSONS.md first.
5. Test: one unit test for the new behavior.

Rules:
- Print a short scope block (TO EDIT, OUT OF SCOPE, VERIFY) before editing.
- Respect dependency direction web -> service -> persistence -> core. If a new status/core model or a migration is needed, STOP and tell the user to run /epic.
- PreparedStatement only. Apply role checks the same way as sibling endpoints.
- Verify with `./quick.sh test <TestClass>` or `./quick.sh web`. Pass -> stop.
- Doc sync (mandatory here): update docs/API.md for the new or changed endpoint, run ./gen-codemap.sh if classes were added, and run ./quick.sh docs.
- End with the API CONTRACT in 4 lines (method, path, request body, response body). If the frontend must change, suggest `/ui` with that contract.
- Do not commit. Final reply ends with the Docs line.
