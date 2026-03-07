# Remember Me Feature (Jason Keyrouz)

## Goal
Allow users to stay logged in using a persistent cookie.

## How?
1. User logs in with "Remember me" checked (waiting for the login page)
2. The Backend generates a token
3. Token stored in DataBase
4. Cookie would be sent to browser (30 days)
5. On future requests, cookie is validated

If token invalid/expired → delete cookie
On logout → delete cookie + delete token from DataBase

## TODO:
- Integrate with LOGIN controller
- Integrate with LOGOUT controller

“On each request: if session missing but cookie exists → validate token → auto-login user"