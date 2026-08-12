# page

> Source: https://nginx.org/en/docs/http/ngx_http_oidc_module.html

---

## 目錄

- [Module ngx\_http\_oidc\_module](#module-ngxhttpoidcmodule)
    - [Example Configuration](#example-configuration)
    - [Directives](#directives)
    - [Embedded Variables](#embedded-variables)

---

## Module ngx\_http\_oidc\_module

The `ngx_http_oidc_module` module (1.27.4) implements authentication as a Relying Party in OpenID Connect using the [Authorization Code Flow](https://openid.net/specs/openid-connect-core-1_0.html#CodeFlowAuth).

The module expects the OpenID Provider's configuration to be available via [metadata](https://openid.net/specs/openid-connect-discovery-1_0.html#ProviderConfig) and requires dynamic [resolver](https://nginx.org/en/docs/http/ngx_http_core_module.html#resolver).

The module can be combined with other access modules via the [satisfy](https://nginx.org/en/docs/http/ngx_http_core_module.html#satisfy) directive. Note that the module may still block requests even with `satisfy any;` as an OpenID Provider might not redirect the user back to nginx.

> This module is available as part of our [commercial subscription](https://www.f5.com/products/nginx).

#### Example Configuration

```nginx
http {
    resolver 10.0.0.1;

    oidc_provider my_idp {
        issuer        "https://provider.domain";
        client_id     "unique_id";
        client_secret "unique_secret";
    }

    server {
        location / {
            auth_oidc my_idp;

            proxy_set_header username $oidc_claim_sub;
            proxy_pass       http://backend;
        }
    }
}
```

The example assumes that the “`https://<nginx-host>/oidc_callback`” Redirection URI is configured on the OpenID Provider's side. The path can be customized with the [redirect\_uri](https://nginx.org/en/docs/http/ngx_http_oidc_module.html#redirect_uri) directive.

#### Directives

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>oidc_provider</strong> <code><i>name</i></code> { ... }</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code><br></td></tr></tbody></table>

Defines an OpenID Provider for use with the [auth\_oidc](https://nginx.org/en/docs/http/ngx_http_oidc_module.html#auth_oidc) directive.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>auth_oidc</strong> <code><i>name</i></code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>auth_oidc off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

Enables end user authentication with the [specified](https://nginx.org/en/docs/http/ngx_http_oidc_module.html#oidc_provider) OpenID Provider.

Parameter value can contain variables (1.29.0).

The special value `off` cancels the effect of the `auth_oidc` directive inherited from the previous configuration level.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>issuer</strong> <code><i>URL</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>oidc_provider</code><br></td></tr></tbody></table>

Sets the Issuer Identifier URL of the OpenID Provider; required directive. The URL must exactly match the value of “`issuer`” in the OpenID Provider metadata and requires the “`https`” scheme.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>client_id</strong> <code><i>string</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>oidc_provider</code><br></td></tr></tbody></table>

Specifies the client ID of the Relying Party; required directive.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>client_secret</strong> <code><i>string</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>oidc_provider</code><br></td></tr></tbody></table>

Specifies a secret value used to authenticate the Relying Party with the OpenID Provider. The supported [authentication methods](https://openid.net/specs/openid-connect-core-1_0.html#ClientAuthentication) are `client_secret_basic` and `client_secret_post` (1.29.3). The method is selected based on the OpenID Provider metadata with a preference to `client_secret_basic`.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>config_url</strong> <code><i>URL</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>config_url &lt;issuer&gt;/.well-known/openid-configuration;</pre></td></tr><tr><th>Context:</th><td><code>oidc_provider</code><br></td></tr></tbody></table>

Sets a custom URL to retrieve the OpenID Provider metadata.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>cookie_name</strong> <code><i>name</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>cookie_name NGX_OIDC_SESSION;</pre></td></tr><tr><th>Context:</th><td><code>oidc_provider</code><br></td></tr></tbody></table>

Sets the name of a session cookie.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>extra_auth_args</strong> <code><i>string</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>oidc_provider</code><br></td></tr></tbody></table>

Sets additional query arguments for the [authentication request](https://openid.net/specs/openid-connect-core-1_0.html#AuthRequest) URL.

```nginx
extra_auth_args "display=page&prompt=login";
```

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>frontchannel_logout_uri</strong> <code><i>uri</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>oidc_provider</code><br></td></tr></tbody></table>

This directive appeared in version 1.29.3.

Defines the URI path for triggering [front-channel logout](https://openid.net/specs/openid-connect-frontchannel-1_0.html). For the logout request to be associated with a user session, it must either include the module session cookie or provide both the “`iss`” and “`sid`” arguments. It is recommended to configure the OpenID Provider to set the “`iss`” and “`sid`” arguments when invoking this endpoint.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>pkce</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>oidc_provider</code><br></td></tr></tbody></table>

This directive appeared in version 1.29.3.

Explicitly enables or disables PKCE. By default, PKCE is automatically enabled based on OpenID Provider metadata.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>redirect_uri</strong> <code><i>uri</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>redirect_uri /oidc_callback;</pre></td></tr><tr><th>Context:</th><td><code>oidc_provider</code><br></td></tr></tbody></table>

Defines the Redirection URI path for post-authentication redirects expected by the module from the OpenID Provider. The `*uri*` must match the configuration on the Provider's side.

Absolute “`https`” URIs are supported since 1.29.0.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>logout_uri</strong> <code><i>uri</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>oidc_provider</code><br></td></tr></tbody></table>

This directive appeared in version 1.29.0.

Defines the URI path for initiating session logout. Upon session termination, the user is redirected to [Provider's Logout Endpoint](https://openid.net/specs/openid-connect-rpinitiated-1_0.html#OPMetadata) or to the [post logout page](https://nginx.org/en/docs/http/ngx_http_oidc_module.html#post_logout_uri). If neither is configured, the built-in post logout page is displayed.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>post_logout_uri</strong> <code><i>uri</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>oidc_provider</code><br></td></tr></tbody></table>

This directive appeared in version 1.29.0.

Defines the path or absolute URI to redirect the user to after the logout. The `*uri*` must match the configuration on the Provider's side. If the post logout page is served by NGINX, the OIDC module shouldn't be enabled for this location:

```nginx
http {
    oidc_provider my_idp {
        ...

        logout_uri      /logout;
        post_logout_uri /logged_out_page.html;
    }

    server {
        auth_oidc my_idp;

        location /logged_out_page.html {
            auth_oidc off;
        }
    }
}
```

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>logout_token_hint</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>logout_token_hint off;</pre></td></tr><tr><th>Context:</th><td><code>oidc_provider</code><br></td></tr></tbody></table>

This directive appeared in version 1.29.0.

Adds the [`id_token_hint`](https://openid.net/specs/openid-connect-rpinitiated-1_0.html#RPLogout) argument to the [Provider's Logout Endpoint](https://openid.net/specs/openid-connect-rpinitiated-1_0.html#OPMetadata) when redirecting user during logout. This argument can be required by some OpenID Providers.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>scope</strong> <code><i>scope</i></code> ...;</code><br></td></tr><tr><th>Default:</th><td><pre>scope openid;</pre></td></tr><tr><th>Context:</th><td><code>oidc_provider</code><br></td></tr></tbody></table>

Sets requested scopes. The `openid` scope is always required by OIDC.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>session_store</strong> <code><i>name</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>oidc_provider</code><br></td></tr></tbody></table>

Specifies a custom [key-value database](https://nginx.org/en/docs/http/ngx_http_keyval_module.html#keyval_zone) that stores session data. By default, an 8-megabyte key-value database named `oidc_default_store_<provider name>` is created automatically.

> A separate key-value database should be configured for each Provider to prevent session reuse across providers.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>session_timeout</strong> <code><i>time</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>session_timeout 8h;</pre></td></tr><tr><th>Context:</th><td><code>oidc_provider</code><br></td></tr></tbody></table>

Sets a timeout after which the session is deleted, unless it was [refreshed](https://openid.net/specs/openid-connect-core-1_0.html#RefreshTokens).

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>ssl_crl</strong> <code><i>file</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>oidc_provider</code><br></td></tr></tbody></table>

Specifies a `*file*` with revoked certificates (CRL) in the PEM format used to verify the certificates of the OpenID Provider endpoints. When using intermediate certificates, their CRLs should be specified in the same file.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>ssl_trusted_certificate</strong> <code><i>file</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>ssl_trusted_certificate system CA bundle;</pre></td></tr><tr><th>Context:</th><td><code>oidc_provider</code><br></td></tr></tbody></table>

Specifies a `*file*` with trusted CA certificates in the PEM format used to verify the certificates of the OpenID Provider endpoints.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>userinfo</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>userinfo off;</pre></td></tr><tr><th>Context:</th><td><code>oidc_provider</code><br></td></tr></tbody></table>

This directive appeared in version 1.29.0.

Enables downloading of the [UserInfo](https://openid.net/specs/openid-connect-core-1_0.html#UserInfo) data and makes UserInfo claims available via the [$oidc\_claim\_`name`](https://nginx.org/en/docs/http/ngx_http_oidc_module.html#var_oidc_claim_) variables.

#### Embedded Variables

The `ngx_http_oidc_module` module supports embedded variables:

`$oidc_id_token`

ID token

`$oidc_access_token`

access token

`$oidc_claim_``*name*`

top-level ID token or UserInfo claim

Nested claims can be fetched with the [auth\_jwt](https://nginx.org/en/docs/http/ngx_http_auth_jwt_module.html) module:

```nginx
http {
    auth_jwt_claim_set $postal_code address postal_code;

    server {
        location / {
            auth_oidc my_idp;
            auth_jwt  off token=$oidc_id_token;

            proxy_set_header x-postal_code $postal_code;
            proxy_pass       http://backend;
        }
    }
}
```

`$oidc_userinfo`

UserInfo data in the JSON format (1.29.0)