# HeyMoney

Personal finance tracker for Android: accounts, transactions, budgets, loans and planned payments, plus

- bank SMS and notification alerts turned into one-tap transactions
- receipt scanning with the camera, on the phone or with the optional Claude API
- a local assistant that answers questions about your spending
- JSON backup and restore
- investment news
- peer-to-peer expense sharing through QR codes, with no server

Your data stays on your device. See [terms](docs/terms.md) and [privacy](docs/privacy.md).

## Build

```
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease   # signs with keystore.properties (git-ignored) or SIGNING_* variables
```

## License

GNU General Public License v3.0, see [LICENSE](LICENSE) and [NOTICE](NOTICE).

<!-- chore: trigger CI -->
