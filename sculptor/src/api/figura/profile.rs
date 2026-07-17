use std::ops::Add;
use std::path::PathBuf;
use std::time::{Duration, SystemTime};
use axum::{
    body::Bytes, extract::{Path, State}, Json
};
use tracing::debug;
use serde_json::{json, Value};
use tokio::{
    fs,
    io::{self, AsyncReadExt, BufWriter},
};
use uuid::Uuid;

use crate::{
    api::errors::internal_and_log,
    auth::Token, utils::{calculate_file_sha256, format_uuid},
    ApiError, ApiResult, AppState, AVATARS_VAR
};
use super::websocket::S2CMessage;

pub fn is_requesting_self(uuid: Uuid, state: &AppState, token: &String) -> bool {
    return if let Some(user_info) = state.user_manager.get(token) {
        let user_uuid = user_info.uuid;
        uuid.eq(&user_uuid)
    } else { false };
}

pub async fn user_info(
    Path(uuid): Path<Uuid>,
    Token(token): Token,
    State(state): State<AppState>,
) -> ApiResult<Json<Value>> {
    tracing::info!("Receiving profile information for {}", uuid);

    let formatted_uuid = format_uuid(&uuid);

    let request_temp_state = state.user_manager.request_temp_state(uuid, false);
    let request_self_avatar = is_requesting_self(uuid, &state, &token);
    let temp_avatar_file = format!("{}/temp/{}.moon", *AVATARS_VAR, formatted_uuid);
    let path = PathBuf::from(&temp_avatar_file);
    let outdated = if path.exists() {
        let meta = path.metadata().unwrap();
        let last_modified = meta.modified().unwrap();
        SystemTime::now() > last_modified.add(Duration::from_secs(60))
    } else { false };
    let avatar_file = if !request_temp_state && request_self_avatar && !outdated {
        tracing::info!("Profile {} is self requesting and it is temp", uuid);
        state.user_manager.put_request_temp_state(uuid, true);
        temp_avatar_file
    } else {
        format!("{}/{}.moon", *AVATARS_VAR, formatted_uuid)
    };

    let userinfo = if let Some(info) = state.user_manager.get_by_uuid(&uuid) { info } else {
        return Err(ApiError::BadRequest) // NOTE: Not Found (404) shows badge
    };

    let mut user_info_response = json!({
        "uuid": &formatted_uuid,
        "rank": userinfo.rank,
        "equipped": [],
        "lastUsed": userinfo.last_used,
        "equippedBadges": {
            "special": [0,0,0,0,0,0],
            "pride": [0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0]
        },
        "version": userinfo.version,
        "banned": userinfo.banned
    });

    if let Some(settings) = state.config.read().await.advanced_users.clone().get(&uuid) {
        let badges = user_info_response
            .get_mut("equippedBadges")
            .and_then(Value::as_object_mut)
            .unwrap();
        badges.append(
            json!({
                "special": settings.special,
                "pride": settings.pride
            })
            .as_object_mut()
            .unwrap(),
        )
    }

    if fs::metadata(&avatar_file).await.is_ok() {
        if let Some(equipped) = user_info_response
            .get_mut("equipped")
            .and_then(Value::as_array_mut)
        {
            match calculate_file_sha256(&avatar_file) {
                Ok(hash) => equipped.push(json!({
                    "id": "avatar",
                    "owner": &formatted_uuid,
                    "hash": hash
                })),
                Err(_e) => {}
            }
        }
    }
    Ok(Json(user_info_response))
}

pub async fn download_avatar(
    Path(uuid): Path<Uuid>,
    Token(token): Token,
    State(state): State<AppState>
) -> ApiResult<Vec<u8>> {
    let str_uuid = format_uuid(&uuid);
    tracing::info!("Requesting an avatar: {}", str_uuid);

    let download_self_avatar = is_requesting_self(uuid, &state, &token);
    let temp_avatar_file = format!("{}/temp/{}.moon", *AVATARS_VAR, str_uuid);
    let path = PathBuf::from(temp_avatar_file);
    let (avatar_file, delete_temp) = if download_self_avatar && path.exists() {
        tracing::info!("Avatar of {} is temp avatar.", str_uuid);
        (format!("{}/temp/{}.moon", *AVATARS_VAR, str_uuid), true)
    } else {
        (format!("{}/{}.moon", *AVATARS_VAR, str_uuid), false)
    };

    let mut file = if let Ok(file1) = fs::File::open(avatar_file.clone()).await {
        file1
    } else {
        return Err(ApiError::NotFound)
    };
    let mut buffer = Vec::new();
    file.read_to_end(&mut buffer).await.map_err(internal_and_log)?;
    if delete_temp {
        let to_delete = avatar_file;
        fs::remove_file(to_delete).await.map_err(internal_and_log)?;
    }
    Ok(buffer)
}

pub async fn upload_avatar(
    Token(token): Token,
    State(state): State<AppState>,
    body: Bytes,
) -> ApiResult<String> {
    let request_data = body;

    if let Some(user_info) = state.user_manager.get(&token) {
        tracing::info!(
            "{} ({}) trying to upload an avatar",
            user_info.uuid,
            user_info.nickname
        );
        let def = state.config.read().await.limitations.can_upload;
        let can_upload = state.user_manager.upload_state(user_info.uuid, def);
        if !can_upload {
            return Err(ApiError::Forbidden);
        }
        let avatar_file = format!("{}/{}.moon", *AVATARS_VAR, user_info.uuid);
        let mut file = BufWriter::new(fs::File::create(&avatar_file).await.map_err(internal_and_log)?);
        io::copy(&mut request_data.as_ref(), &mut file).await.map_err(internal_and_log)?;
    }
    Ok("ok".to_string())
}

pub async fn equip_avatar(Token(token): Token, State(state): State<AppState>) -> ApiResult<&'static str> {
    debug!("[API] S2C : Equip");
    let uuid = state.user_manager.get(&token).ok_or(ApiError::Unauthorized)?.uuid;
    send_event(&state, &uuid).await;
    Ok("ok")
}

pub async fn delete_avatar(Token(token): Token, State(state): State<AppState>) -> ApiResult<String> {
    if let Some(user_info) = state.user_manager.get(&token) {
        tracing::info!(
            "{} ({}) is trying to delete the avatar",
            user_info.uuid,
            user_info.nickname
        );
        let avatar_file = format!("{}/{}.moon", *AVATARS_VAR, user_info.uuid);
        fs::remove_file(avatar_file).await.map_err(internal_and_log)?;
        send_event(&state, &user_info.uuid).await;
    }
    Ok("ok".to_string())
}

pub async fn send_event(state: &AppState, uuid: &Uuid) {
    // To user subscribers
    if let Some(broadcast) = state.subscribes.get(uuid) {
        if broadcast.send(S2CMessage::Event(*uuid).into()).is_err() {
            debug!("[WebSocket] Failed to send Event! There is no one to send. UUID: {uuid}")
        };
    } else {
        debug!("[WebSocket] Failed to send Event! Can't find UUID: {uuid}")
    };
    // To user
    if let Some(session) = state.session.get(uuid) {
        if session.send(super::SessionMessage::Ping(S2CMessage::Event(*uuid).into())).await.is_err() {
            debug!("[WebSocket] Failed to send Event! WS doesn't connected? UUID: {uuid}")
        };
    } else {
        debug!("[WebSocket] Failed to send Event! Can't find UUID: {uuid}")
    };
}