use axum::{
    debug_handler,
    extract::{Query, State, ConnectInfo},
    response::{IntoResponse, Response},
    routing::get,
    Router,
    http::HeaderMap
};
use reqwest::StatusCode;
use ring::digest::{self, digest};
use std::net::SocketAddr;
use tracing::{error, info};

use crate::{auth::{has_joined, Userinfo}, utils::rand, AppState};
use super::types::auth::*;

pub fn router() -> Router<AppState> {
    Router::new()
        .route("/id", get(id))
        .route("/verify", get(verify))
}

#[debug_handler]
async fn id(
    // First stage of authentication
    headers: HeaderMap,
    Query(query): Query<Id>,
    State(state): State<AppState>,
    ConnectInfo(addr): ConnectInfo<SocketAddr>,
) -> String {
    let real_ip = headers
        .get("x-forwarded-for")
        .and_then(|v| v.to_str().ok())
        .and_then(|v| v.split(',').next())
        .map(|v| v.trim().to_string())
        .unwrap_or_else(|| addr.ip().to_string());

    info!("[Authentication] ID request from {} (Real: {}) for user {}", addr, real_ip, query.username);
    let server_id =
        faster_hex::hex_string(&digest(&digest::SHA1_FOR_LEGACY_USE_ONLY, &rand()).as_ref()[0..20]);
    let state = state.user_manager;
    state.pending_insert(server_id.clone(), query.username);
    server_id
}

#[debug_handler]
async fn verify(
    // Second stage of authentication
    headers: HeaderMap,
    Query(query): Query<Verify>,
    State(state): State<AppState>,
    ConnectInfo(addr): ConnectInfo<SocketAddr>,
) -> Response {
    let server_id = query.id.clone();
    let nickname = state.user_manager.pending_remove(&server_id).unwrap().1; // TODO: Add error check

    // 获取真实 IP 逻辑：优先从 X-Forwarded-For 获取，没有则用 X-Real-IP，最后退回到 SocketAddr
    let real_ip = headers
        .get("x-forwarded-for")
        .and_then(|v| v.to_str().ok())
        .and_then(|v| v.split(',').next()) // 如果有多个代理，取第一个
        .map(|v| v.trim().to_string())
        .or_else(|| headers.get("x-real-ip").and_then(|v| v.to_str().ok()).map(|s| s.to_string()))
        .unwrap_or_else(|| addr.ip().to_string());

    info!("[Authentication] Verify request from {} (Real: {}) for user {}", addr, real_ip, nickname);

    let userinfo = match has_joined(
        State(state.clone()),
        &server_id,
        &nickname,
        real_ip
    ).await {
        Ok(d) => d,
        Err(_e) => {
            // error!("[Authentication] {e}"); // In auth error log already defined
            return (StatusCode::INTERNAL_SERVER_ERROR, "internal verify error".to_string()).into_response();
        },
    };
    if let Some((uuid, auth_provider)) = userinfo {
        let umanager = state.user_manager;
        if umanager.is_banned(&uuid) {
            info!("[Authentication] {nickname} tried to log in, but was banned");
            return (StatusCode::BAD_REQUEST, "You're banned!".to_string()).into_response();
        }
        info!("[Authentication] {nickname} logged in using {}", auth_provider.name);
        let userinfo = Userinfo {
            nickname,
            uuid,
            token: Some(server_id.clone()),
            auth_provider,
            ..Default::default()
        };
        match umanager.insert(uuid, server_id.clone(), userinfo.clone()) {
            Ok(_) => {},
            Err(_) => {
                umanager.remove(&uuid);
                if umanager.insert(uuid, server_id.clone(), userinfo).is_err() {
                    error!("Old token error after attempting to remove it! Unexpected behavior!");
                    return (StatusCode::BAD_REQUEST, "second session detected".to_string()).into_response();
                };
            }
        }
        (StatusCode::OK, server_id.to_string()).into_response()
    } else {
        info!("[Authentication] failed to verify {nickname}");
        (StatusCode::BAD_REQUEST, "failed to verify".to_string()).into_response()
    }
}