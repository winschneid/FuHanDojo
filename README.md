# 符ハン道場 - 麻雀点数計算トレーニング

英名: Fu Han Dojo - Riichi Mahjong Scoring

初心者が符と翻から点数を出せるようになるための、4択クイズ形式の Android アプリです。
10級から順に、10問中8問正解で合格すると次の級に進めます。

| 級 | 内容 |
|---|---|
| 10級 | 満貫以上の名前（翻数 → 満貫・跳満・倍満・三倍満・役満） |
| 9級 | 子の満貫以上（ロン） |
| 8級 | 親の満貫以上（ロン） |
| 7級 | 満貫以上のツモ |
| 6級 | 子のロン 30符・40符 |
| 5級 | 親のロン 30符・40符 |
| 4級 | ロン 全符（25〜110符） |
| 3級 | ツモ 30符・40符 |
| 2級 | ツモ 全符（20〜110符） |
| 1級 | 総合 |

点数は切り上げ満貫なし（30符4翻 = 7700）、13翻以上は数え役満の一般的なルールで計算します。

## 構成

- Kotlin / Jetpack Compose / Material 3
- Hilt（DI）、DataStore（級ごとの最高正解数を保存）
- `domain/ScoreCalculator.kt` 点数計算、`domain/QuizGenerator.kt` 出題と解説

## ビルドとテスト

```
./gradlew assembleDebug
./gradlew testDebugUnitTest
```
