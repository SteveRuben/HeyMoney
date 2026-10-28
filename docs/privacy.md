# HeyMoney - Privacy Policy

_Last updated: 2026-10-28_

**Short version:** HeyMoney keeps your financial data on your phone. There is no HeyMoney account and no HeyMoney server.

## Data stored on your device
Accounts, transactions, categories, budgets, loans, settings, pending bank alerts and your optional Anthropic API token
(stored encrypted with the Android keystore). This data is not sent to the developers.

## What the app reads, only if you allow it
- **SMS** (permission RECEIVE_SMS): incoming messages are scanned on the phone to find bank transactions.
  Only messages that look like a bank transaction create a draft; nothing is uploaded.
- **Notifications** (notification access): notifications from other apps are scanned the same way, on the phone only.
- **Camera / photos**: receipt photos are read on the phone (Google ML Kit text recognition, offline).
  Photos are not kept by HeyMoney after reading, except in the temporary app cache.

## Network use
- **Optional AI reading:** only if you save an Anthropic API token. The receipt photo is then sent to the Anthropic API
  (api.anthropic.com) to extract the merchant, total and date. Anthropic's terms and privacy policy apply to that request.
  Remove the token in Settings > AI to stop.
- **Exchange rates** are fetched from public currency data sources, with no personal data.
- **Investment news** is fetched from public RSS feeds (Google News, Yahoo Finance, MarketWatch). These services can see
  your IP address like any web request.
- **Releases and contributors** screens read public GitHub data.
- The sharing feature works between two phones through a QR code or a link you send yourself; HeyMoney uploads nothing.

## Third-party components
HeyMoney includes third-party libraries, among them Firebase (used only by an in-app poll feature that is
disabled in HeyMoney) and Google ML Kit. HeyMoney does not define its own analytics or advertising.

## Your choices
You can deny or revoke any permission, remove the AI token, export or delete your data from Settings,
or uninstall the app, which removes all local data.

Questions: https://github.com/SteveRuben/HeyMoney/issues
