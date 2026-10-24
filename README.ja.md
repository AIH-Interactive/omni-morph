# OmniMorph

[简体中文](README.md) · **日本語** · [English](README.en.md) · [한국어](README.ko.md)

OmniMorph は [Figura](https://github.com/FiguraMC/Figura) をベースにした Minecraft Java 用クライアント Mod です。Figura Avatar と YSM モデルを、共通のワードローブ、プレビュー、操作画面で使用できます。

Lua と Molang の両ランタイムに対応しています。Lua はスクリプトや操作を、Molang はモデルの式やアニメーション制御を担当します。YSM モデルは、追加の Lua スクリプトを作成せずに読み込んで実行できます。

[ビルドのダウンロード](https://github.com/AIH-Interactive/omni-morph/actions/workflows/fabric-build.yml) · [更新履歴](docs/CHANGELOG.ja.md) · [不具合の報告](https://github.com/AIH-Interactive/omni-morph/issues) · [対応バックエンド](https://github.com/AIH-Interactive/sculptor-for-omni-morph)

## 機能

- **モデル読み込みの統合**：Figura のモデルフォルダーと ZIP、および対応形式の YSM フォルダー、ZIP、暗号化された `.ysm` ファイル。
- **モデル操作**：ワードローブの絞り込み、モデルプレビュー、テクスチャ切り替え、モデル専用設定、アクションホイール。
- **アニメーションとスクリプト**：YSM アニメーションコントローラー、Molang 式、Figura Lua API と YSM インターフェースの連携。
- **モデルライブラリ**：アップロード、分類、検索、お気に入り、ダウンロード、装備、基本情報の編集。
- **アクセス制御と管理**：公開・非公開モデル、利用許可の管理、通報、異議申し立て、モデルのブロック、ごみ箱からの復元。
- **マルチプレイでの共有**：対応バックエンドによるモデル配信と動的状態の同期。Figura Cloud と Protobuf に対応。
- **任意のハードウェアアクセラレーション**：SIMD、GPU、自動モードを用意。初期設定では無効で、利用できない場合は Java 処理にフォールバック。
- **画面の言語**：簡体字中国語と英語を同梱。

## 動作環境

| 項目 | バージョン |
| --- | --- |
| Minecraft Java | 26.1.2 |
| Mod ローダー | Fabric |
| Java | 25 |
| Mod バージョン | 1.0.0-alpha.4 |

ビルド依存関係のバージョンは [gradle.properties](gradle.properties) を参照してください。本 Mod はクライアント用です。マルチプレイでのリソース配信と状態同期には、対応バックエンドが必要です。

## インストールと使い方

1. Minecraft 26.1.2 用の Fabric をインストールし、Java 25 でゲームを起動します。
2. 成功したワークフロー実行から最終成果物の Mod JAR をダウンロードし、対象のゲームインスタンスの `mods` フォルダーに配置します。
3. ゲームを起動して OmniMorph のワードローブを開き、モデルフォルダーにモデルを置いて選択します。
4. モデル専用設定とアクションホイールでモデルを操作します。モデルライブラリでは、クラウドモデルのアップロード、管理、ダウンロードができます。

既定のモデルフォルダーは `.minecraft/omnimorph/avatars` です。設定で OmniMorph フォルダーの場所を変更できます。ワードローブのフォルダーボタンから、実際に使用しているモデルディレクトリを開けます。

設定は `omni-morph.json` に保存されます。初回の移行時には、既存の `figura.json` を読み込み、元の設定ファイルを残すことができます。

利用できる機能はモデル形式やバージョンによって異なります。モデルのスクリプト、必要なリソース、権限、バックエンドのポリシーも機能に影響します。

## Lua と Molang

OmniMorph は Figura の Lua 環境を維持し、YSM モデル、アニメーション、アクションを扱う `ysm_model` と `ysm_actions` インターフェースを提供します。`ysm_model:isYsm()` で現在のモデル形式を確認できます。モデルの部品、テクスチャ、アニメーションを Lua スクリプトと連携できます。

YSM モデルの式とアニメーションコントローラーは Molang ランタイムで処理されます。モデルに含まれるコントロール、アクション、言語リソースは、対応する画面で使用されます。独自のモデル名や内容はモデル作者が定義します。

## バックエンドへの接続

設定でサーバーアドレスを入力し、サーバーが対応するプロトコルを選択してください。

- **Protobuf**：既定のプロトコルです。OmniMorph Protobuf に対応したバックエンドが必要です。
- **Figura Cloud**：既存の Figura Cloud サービスとの互換性を保つためのプロトコルです。

プロトコルに互換性がない場合、クライアントは自動で別のプロトコルに切り替えません。サーバーアドレスは既定で HTTPS/WSS を使用し、明示的な HTTP/WS はデバッグモードでのみ許可されます。接続に失敗してもローカルモデルは読み込めます。

対応バックエンドとして [sculptor-for-omni-morph](https://github.com/AIH-Interactive/sculptor-for-omni-morph) を推奨します。設定とデプロイ方法は、バックエンドの [デプロイガイド](https://github.com/AIH-Interactive/sculptor-for-omni-morph/blob/master/docs/DEPLOYMENT.md) を参照してください。

## ソースからのビルド

JDK 25 を用意し、`JAVA_HOME` と `PATH` を設定して、リポジトリのルートで実行します。

```powershell
& .\gradlew.bat :fabric:build --console=plain 2>&1 | Tee-Object -FilePath 'build.log'
$buildExitCode = $LASTEXITCODE
if ($buildExitCode -ne 0) {
    Get-Content -LiteralPath 'build.log' -Tail 40
    exit $buildExitCode
}
```

最終成果物は `fabric/build/libs/` に生成され、ファイル名は `omni-morph-...-fabric-mc.jar` です。

通常の Fabric ビルドに C++ ツールチェーンは不要です。ローカル SIMD ライブラリのビルド方法は [native/README.md](native/README.md)、プロトコル仕様は [protocol/README.md](protocol/README.md) を参照してください。

## 謝辞

以下のプロジェクトと貢献者の皆様に感謝します。

- [Figura](https://github.com/FiguraMC/Figura)：Avatar、Lua、権限、画面のコード基盤。
- [YesSteveModel](https://github.com/YesSteveModel/YesSteveModel) と [YesSteveModel-Native](https://github.com/YesSteveModel/YesSteveModel-Native)：YSM モデルのエコシステムと実装の参考。
- [Sparkle-Morpher](https://github.com/sdf123098/Sparkle-Morpher)、[OpenYSM / YSMParser](https://github.com/OpenYSM/YSMParser)：モデルの実行動作と形式解析の参考。
- [openysm.cpp](https://github.com/OpenYSMDev/openysm.cpp)：SIMD/native アルゴリズムの比較参考。
- [Sculptor](https://github.com/MrXiaoM/sculptor) と [spm-cloud](https://github.com/sdf123098/spm-cloud)：バックエンドとプロトコル設計の参考。
- [FiguraMC/Assets](https://github.com/FiguraMC/Assets)：簡体字中国語の画面翻訳の一部。

貢献者と第三者由来の情報は [CREDITS](CREDITS)、および同梱のライセンス・帰属表示ファイルを参照してください。上流プロジェクトの紹介は [Figura オリジナル README](docs/README.Figura.original.md) に保存しています。

## AI による開発支援

OmniMorph はコード実装、問題調査、テスト、文書整備に AI ツールを使用しています。プロジェクトの方向性、対応範囲の判断、リリースはメンテナーが責任を持ちます。機能の完成は、実際のコード、ビルド結果、テスト、受け入れ確認の記録に基づいて判断します。

Mod アイコンを含む一部の画像素材は OpenAI GPT Image で生成し、その後に編集を加えています。

## ライセンス

本プロジェクトは [PolyForm Noncommercial License 1.0.0](LICENSE.md) を採用しています。第三者のコード、依存関係、素材には、それぞれに付属するライセンスが適用されます。
