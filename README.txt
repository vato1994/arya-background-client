Arya Background Client - Website Included Version

Flow:
1. App opens your included website automatically:
   https://vato1994.github.io/chatplogin/
2. First time only: tap "Allow Background & Continue".
3. That permission screen hides.
4. Use the website UI normally.
5. When you tap Login All IDs on the website, Android native foreground service starts automatically.
6. When you join rooms from the website UI, native service receives room IDs and keeps them joined in background.

To change hosted website URL:
app/src/main/res/values/strings.xml
change hosted_website_url only.

Build in GitHub Actions as before.
