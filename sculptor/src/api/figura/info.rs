use axum::{extract::State, Json};
use serde_json::{json, Value};
use tracing::error;

use crate::{
    utils::{get_figura_versions, get_motd, FiguraVersions}, AppState, FIGURA_DEFAULT_VERSION
};
use crate::auth::Token;

pub async fn version(State(state): State<AppState>) -> Json<FiguraVersions> {
    let res = state.figura_versions.read().await.clone();
    if let Some(res) = res {
        Json(res)
    } else {
        let actual = get_figura_versions().await;
        if let Ok(res) = actual {
            let mut stored = state.figura_versions.write().await;
            *stored = Some(res);
            return Json(stored.clone().unwrap())
        } else {
            error!("get_figura_versions: {:?}", actual.unwrap_err());
        }
        Json(FiguraVersions {
            release: FIGURA_DEFAULT_VERSION.to_string(),
            prerelease: FIGURA_DEFAULT_VERSION.to_string()
        })
    }
}

pub async fn motd(State(state): State<AppState>) -> Json<Vec<crate::utils::Motd>> {
    Json(get_motd(state).await)
}

pub async fn limits(
    Token(token): Token,
    State(state): State<AppState>
) -> Json<Value> {
    let limits = &state.config.read().await.limitations;
    let can_upload = if let Some(user_info) = state.user_manager.get(&token) {
        state.user_manager.upload_state(user_info.uuid, limits.can_upload)
    } else {
        limits.can_upload
    };
    Json(json!({
        "rate": {
            "pingSize": 1024,
            "pingRate": 32,
            "equip": 1,
            "download": 50,
            "upload": 1
        },
        "limits": {
            "maxAvatarSize": limits.max_avatar_size * 1000,
            "maxAvatars": limits.max_avatars,
            "canUpload": can_upload,
            "allowedBadges": {
                "special": [0,0,0,0,0,0],
                "pride": [0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0]
            }
        }
    }))
}
