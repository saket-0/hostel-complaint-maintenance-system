---
description: Implement a new REST endpoint spanning Controller -> DTO -> Service -> DAO with unit test.
---

# Endpoint Workflow

Layer Sequence:
1. **Controller**: Update or add endpoint method in `hcms-web/src/main/java/edu/vit/hcms/web/controller/{ControllerName}.java`.
2. **DTO**: Add or update request/response DTO in `hcms-web/src/main/java/edu/vit/hcms/web/dto/`.
3. **Service**: Implement business logic in `hcms-service/src/main/java/edu/vit/hcms/service/complaint/ComplaintManager.java` (or relevant service).
4. **DAO**: Add or update persistence query in `hcms-persistence/src/main/java/edu/vit/hcms/persistence/dao/` ONLY if a new query is required.
5. **Test**: Add one unit test covering the new endpoint or service method.
6. **Verify**: Run `./quick.sh test <TestClass>` or `./quick.sh web`.
7. **Do NOT** touch domain enums or core models unless explicitly required by a new status.
