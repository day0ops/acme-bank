//! JWT validation and role-based authorization for the Notifications REST API.
//!
//! Tokens are expected to carry Keycloak's `realm_access.roles` claim. Per
//! the confirmed deployment, Keycloak expands composite roles into that
//! claim before signing (an `admin` token's `roles` already includes
//! `"banker"`), so role checks here only ever need literal membership
//! testing - no special-casing of `admin` as a superset.

use axum::extract::Request;
use axum::http::{header, HeaderMap, StatusCode};
use axum::middleware::Next;
use axum::response::{IntoResponse, Response};
use jsonwebtoken::jwk::{AlgorithmParameters, JwkSet};
use jsonwebtoken::{decode, Algorithm, DecodingKey, Validation};
use serde::{Deserialize, Serialize};
use std::fmt;
use std::future::Future;
use std::pin::Pin;
use std::sync::Arc;

/// Subset of a validated JWT's claims this service cares about.
#[derive(Debug, Clone)]
pub struct Claims {
    pub subject: String,
    pub roles: Vec<String>,
}

/// Failure modes for JWT validation, role enforcement, and JWKS setup.
#[derive(Debug)]
pub enum AuthError {
    /// Missing/malformed `Authorization: Bearer` header, or the token failed
    /// signature/expiry validation. Maps to `401 Unauthorized`.
    InvalidToken,
    /// The token is valid but the caller lacks every required role. Maps to
    /// `403 Forbidden`.
    MissingRole,
    /// Fetching or parsing the JWKS document failed. Only produced by
    /// `Verifier::from_jwks_url` at startup, never surfaced to a client.
    JwksFetchFailed(String),
}

impl fmt::Display for AuthError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            AuthError::InvalidToken => write!(f, "invalid or missing bearer token"),
            AuthError::MissingRole => write!(f, "caller lacks a required role"),
            AuthError::JwksFetchFailed(detail) => write!(f, "failed to fetch JWKS: {detail}"),
        }
    }
}

impl std::error::Error for AuthError {}

impl IntoResponse for AuthError {
    fn into_response(self) -> Response {
        match self {
            AuthError::InvalidToken => StatusCode::UNAUTHORIZED.into_response(),
            AuthError::MissingRole => StatusCode::FORBIDDEN.into_response(),
            AuthError::JwksFetchFailed(_) => StatusCode::INTERNAL_SERVER_ERROR.into_response(),
        }
    }
}

#[derive(Serialize, Deserialize)]
struct RealmAccess {
    roles: Vec<String>,
}

#[derive(Serialize, Deserialize)]
struct RawClaims {
    sub: String,
    exp: usize,
    realm_access: RealmAccess,
}

/// Validates RS256-signed JWTs against a trusted public key.
pub struct Verifier {
    decoding_key: DecodingKey,
}

impl Verifier {
    /// Builds a verifier backed by a single known public key (test use).
    pub fn from_key(decoding_key: DecodingKey) -> Self {
        Self { decoding_key }
    }

    /// Builds a verifier backed by Keycloak's live JWKS endpoint (production
    /// use). `jsonwebtoken` doesn't fetch JWKS itself, so this does a plain
    /// HTTP GET and parses the response into a `DecodingKey`, taking the
    /// first RSA key found. The realm this system targets is configured
    /// with a single active RS256 signing key, so `kid`-based multi-key
    /// rotation isn't implemented.
    pub async fn from_jwks_url(url: &str) -> Result<Self, AuthError> {
        let response = reqwest::get(url)
            .await
            .map_err(|err| AuthError::JwksFetchFailed(err.to_string()))?;
        let jwk_set: JwkSet = response
            .json()
            .await
            .map_err(|err| AuthError::JwksFetchFailed(err.to_string()))?;
        let rsa_jwk = jwk_set
            .keys
            .iter()
            .find(|jwk| matches!(jwk.algorithm, AlgorithmParameters::RSA(_)))
            .ok_or_else(|| AuthError::JwksFetchFailed("no RSA key in JWKS response".to_string()))?;
        let decoding_key = DecodingKey::from_jwk(rsa_jwk)
            .map_err(|err| AuthError::JwksFetchFailed(err.to_string()))?;
        Ok(Self { decoding_key })
    }

    /// Validates `token`'s signature and expiry, returning its subject and
    /// effective (composite-expanded) realm roles.
    pub fn validate(&self, token: &str) -> Result<Claims, AuthError> {
        let mut validation = Validation::new(Algorithm::RS256);
        // Keycloak's default "audience resolve" mapper adds an `aud` claim,
        // but this service (like Go/Java's validators) doesn't check it -
        // without this, jsonwebtoken's default `validate_aud = true` would
        // reject otherwise-legitimate tokens that happen to carry one.
        validation.validate_aud = false;
        let data = decode::<RawClaims>(token, &self.decoding_key, &validation)
            .map_err(|_| AuthError::InvalidToken)?;
        Ok(Claims {
            subject: data.claims.sub,
            roles: data.claims.realm_access.roles,
        })
    }
}

fn bearer_token(headers: &HeaderMap) -> Option<&str> {
    headers
        .get(header::AUTHORIZATION)?
        .to_str()
        .ok()?
        .strip_prefix("Bearer ")
}

/// Builds axum middleware requiring a valid Bearer JWT whose roles intersect
/// `roles`. An empty `roles` means "any authenticated caller" - a validated
/// token is enough, no specific role is required.
///
/// axum's `from_fn` has no built-in way to parameterize a handler per route,
/// so this returns a fresh closure (capturing `verifier` and `roles`) each
/// time it's called - apply a separate call per route/method that needs its
/// own role requirement (see `rest::router`).
pub fn require_role(
    verifier: Arc<Verifier>,
    roles: &'static [&'static str],
) -> impl Fn(Request, Next) -> Pin<Box<dyn Future<Output = Response> + Send>> + Clone {
    move |req: Request, next: Next| {
        let verifier = verifier.clone();
        Box::pin(async move {
            let claims = match bearer_token(req.headers()) {
                Some(token) => verifier.validate(token),
                None => Err(AuthError::InvalidToken),
            };
            match claims {
                Ok(claims)
                    if roles.is_empty()
                        || claims
                            .roles
                            .iter()
                            .any(|role| roles.contains(&role.as_str())) =>
                {
                    next.run(req).await
                }
                Ok(_) => AuthError::MissingRole.into_response(),
                Err(err) => err.into_response(),
            }
        })
    }
}

/// Real-JWT test helpers shared by this module's tests and `rest`'s tests.
/// Every test token is a genuine RS256-signed JWT produced from a locally
/// generated key pair and validated through the real `Verifier`/middleware
/// code above - never a mocked "already authenticated" shortcut.
#[cfg(test)]
pub(crate) mod test_support {
    use super::*;
    use jsonwebtoken::{encode, EncodingKey, Header};
    use rsa::pkcs1::{EncodeRsaPrivateKey, EncodeRsaPublicKey};
    use rsa::{RsaPrivateKey, RsaPublicKey};

    /// A locally-generated RSA key pair: `decoding_key` is handed to
    /// `Verifier::from_key`, and `sign` produces tokens that key will accept.
    pub(crate) struct TestKeyPair {
        encoding_key: EncodingKey,
        pub(crate) decoding_key: DecodingKey,
    }

    impl TestKeyPair {
        pub(crate) fn generate() -> Self {
            let mut rng = rand::thread_rng();
            let private_key = RsaPrivateKey::new(&mut rng, 2048).unwrap();
            let public_key = RsaPublicKey::from(&private_key);

            let private_pem = private_key.to_pkcs1_pem(Default::default()).unwrap();
            let public_pem = public_key.to_pkcs1_pem(Default::default()).unwrap();

            Self {
                encoding_key: EncodingKey::from_rsa_pem(private_pem.as_bytes()).unwrap(),
                decoding_key: DecodingKey::from_rsa_pem(public_pem.as_bytes()).unwrap(),
            }
        }

        /// Signs a real RS256 JWT carrying `roles` in `realm_access.roles`,
        /// matching the claim shape Keycloak issues.
        pub(crate) fn sign(&self, roles: &[&str]) -> String {
            let claims = RawClaims {
                sub: "test-user-id".to_string(),
                exp: (std::time::SystemTime::now()
                    .duration_since(std::time::UNIX_EPOCH)
                    .unwrap()
                    .as_secs()
                    + 3600) as usize,
                realm_access: RealmAccess {
                    roles: roles.iter().map(|role| role.to_string()).collect(),
                },
            };
            encode(&Header::new(Algorithm::RS256), &claims, &self.encoding_key).unwrap()
        }

        /// Signs a token like `sign`, but also carrying an `aud` claim, as
        /// Keycloak's default "audience resolve" mapper does.
        pub(crate) fn sign_with_audience(&self, roles: &[&str], aud: &str) -> String {
            #[derive(Serialize)]
            struct ClaimsWithAudience {
                sub: String,
                exp: usize,
                realm_access: RealmAccess,
                aud: String,
            }

            let claims = ClaimsWithAudience {
                sub: "test-user-id".to_string(),
                exp: (std::time::SystemTime::now()
                    .duration_since(std::time::UNIX_EPOCH)
                    .unwrap()
                    .as_secs()
                    + 3600) as usize,
                realm_access: RealmAccess {
                    roles: roles.iter().map(|role| role.to_string()).collect(),
                },
                aud: aud.to_string(),
            };
            encode(&Header::new(Algorithm::RS256), &claims, &self.encoding_key).unwrap()
        }
    }
}

#[cfg(test)]
mod tests {
    use super::test_support::TestKeyPair;
    use super::*;

    #[test]
    fn validate_valid_token_succeeds() {
        let keys = TestKeyPair::generate();
        let token = keys.sign(&["customer"]);
        let verifier = Verifier::from_key(keys.decoding_key);

        let claims = verifier.validate(&token).unwrap();

        assert_eq!(claims.subject, "test-user-id");
        assert_eq!(claims.roles, vec!["customer"]);
    }

    #[test]
    fn validate_wrong_key_fails() {
        let keys = TestKeyPair::generate();
        let token = keys.sign(&["customer"]);
        let other_keys = TestKeyPair::generate();
        let verifier = Verifier::from_key(other_keys.decoding_key);

        assert!(verifier.validate(&token).is_err());
    }

    #[test]
    fn validate_token_with_audience_claim_succeeds() {
        // Keycloak's default "audience resolve" mapper adds an `aud` claim.
        // This service doesn't check audience (matching Go/Java), so such
        // tokens must still be accepted.
        let keys = TestKeyPair::generate();
        let token = keys.sign_with_audience(&["customer"], "acme-bank-web");
        let verifier = Verifier::from_key(keys.decoding_key);

        let claims = verifier.validate(&token).unwrap();

        assert_eq!(claims.subject, "test-user-id");
        assert_eq!(claims.roles, vec!["customer"]);
    }
}
