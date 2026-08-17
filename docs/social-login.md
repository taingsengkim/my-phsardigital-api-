# Social login (Google / Facebook)

Signing in with Google never touches this API's `/auth/register`. The browser goes
to Keycloak, Keycloak talks to Google, and the first thing we ever see is a bearer
token for an account we have no record of. So the work splits in two:

- **Keycloak** owns the sign-in itself. That part is configuration, not code, and
  is what this document covers.
- **This API** records the account the first time it sees a token for it. That part
  is already built — see `UserProvisioningService` and `UserProvisioningFilter`.

## What the API does on its own

Every authenticated request reconciles the caller's row in `users` against their
token: created if missing, refreshed when a claim changed. Nothing needs to call a
"sync" endpoint for this to happen, and it works the same for password accounts,
social accounts, admin-console accounts and realm imports.

`GET /api/v1/auth/me` does the same reconciliation but **reports** failures instead
of logging them, and returns the caller's roles and seller status. Call it once
right after sign-in — it is how the frontend finds out whether to route the user to
the shop dashboard or the buyer home, and it is where a misconfigured provider will
tell you what is wrong.

Claims consumed: `sub`, `email`, `email_verified`, `preferred_username`,
`given_name`, `family_name`, `name`, `picture`, `identity_provider`.

## Keycloak setup

### 1. Register the app with the provider

**Google** — Google Cloud Console → APIs & Services → Credentials → OAuth 2.0
Client ID (type: Web application). Authorised redirect URI:

```
http://localhost:7777/realms/phsardigital/broker/google/endpoint
```

**Facebook** — Meta for Developers → your app → Facebook Login → Settings. Valid
OAuth redirect URI:

```
http://localhost:7777/realms/phsardigital/broker/facebook/endpoint
```

Swap the host for your real domain in production. The `broker/<alias>/endpoint`
shape is fixed by Keycloak — the `<alias>` must match the provider alias you pick
in the next step.

### 2. Add the identity provider in Keycloak

Admin console → realm `phsardigital` → **Identity providers** → Google (or
Facebook). Paste the client ID and secret. Then set:

| Setting | Value | Why |
|---|---|---|
| **Trust Email** | **On** | Without it Keycloak forces its own email-verification step on someone Google has already verified, and `email_verified` arrives false. |
| **Sync mode** | `Force` | Re-imports name and picture on every login, so a user who changes their Google photo sees it change here. |
| **First login flow** | `first broker login` | Handles the duplicate-email case — see below. |

### 3. Give new users the `USER` role — required

`POST /auth/register` grants the `USER` realm role explicitly. **A social sign-in
never runs that code**, so without this step a Google user authenticates fine and
is then refused by every endpoint gated on `hasRole("USER")` — carts, purchases,
seller applications.

Realm settings → **User registration** → **Default roles** → add `USER`.

This must be done in Keycloak rather than here, because the role has to be inside
the very first token Keycloak issues. Granting it from the backend afterwards would
not appear until the user refreshed their token.

### 4. Account linking — read this before testing

If someone registers with a password as `sok@gmail.com` and later clicks "Sign in
with Google" for the same address, Keycloak must recognise them as **one** user. If
it instead creates a second account, that account has a different `sub`, and since
`users.email` is unique this API answers:

```
409  An account with this email already exists. Sign in with the method you used
     originally, then link your social account from your profile.
```

That message means Keycloak, not the API, needs fixing. The default
`first broker login` flow detects the existing account and offers to link it;
combined with **Trust Email** it can link without a challenge. Verify this works
before going near production — it is the single most common way a social login
setup goes wrong, and it is invisible until a real user hits it.

### 5. Optional claim mappers

Two fields stay null unless Keycloak is told to send them. Neither breaks anything
by being absent.

- **`picture`** → fills `externalAvatarUrl`, used as the avatar when the user has
  not uploaded one of their own. Add an *Attribute Importer* mapper on the identity
  provider (claim `picture`), then a *User Attribute* mapper on the client to put it
  in the token.
- **`identity_provider`** → fills `identityProvider`, so you can tell how an account
  was created. Add a *Hardcoded claim* mapper on the identity provider.

## Testing it

1. Sign in with Google from the frontend.
2. Call `GET /api/v1/auth/me` with the returned token.
3. Expect `200` with your email, `roles` containing `USER`, and `isSeller: false`.
4. Check the `users` table — there should be exactly one row for that email.

Failure modes worth recognising:

| Symptom | Cause |
|---|---|
| `409` from `/auth/me` | Duplicate Keycloak account — step 4. |
| `422` from `/auth/me` | Provider withheld the email. Facebook does this when the user hides it; there is nothing to do server-side. |
| `403` on carts/purchases | `USER` is not a default realm role — step 3. |
| Avatar missing for Google users | `picture` mapper not configured — step 5. |
