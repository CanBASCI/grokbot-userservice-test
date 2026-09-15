# edge/

Default Compose does **not** run nginx/Caddy as the API.

Public HTTP is **api-gateway:8080**. Put an optional TLS terminator in front of the
gateway only if Archon/Clerk ask for it — never replace the gateway, and never
publish signup/login gRPC (9090) or `/internal` HTTP here.
