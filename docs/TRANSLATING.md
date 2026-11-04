# Translating Paperclip for Android

The app ships in English (the default), Brazilian Portuguese and Spanish. Adding a language, or fixing a translation, needs no Kotlin: you edit one XML file and register the language. Thank you for helping.

## What you'll edit

| File | What it is |
|---|---|
| `app/src/main/res/values/strings.xml` | The English strings, the source of truth. Don't change it in a translation PR unless you're fixing the English. |
| `app/src/main/res/values-<lang>/strings.xml` | One file per language, for example `values-pt-rBR` or `values-es`. |
| `app/src/main/res/xml/locales_config.xml` | The list of shipped languages. Android's language settings and the app's own picker both read it. |

## Add a language

1. **Pick the folder name.** Android uses `values-<language>` with an optional `-r<REGION>`: `values-fr`, `values-de`, `values-pt-rPT`, `values-zh-rTW`. For scripts or anything more complex, use the BCP 47 form `values-b+sr+Latn`.
2. **Copy the English file:**

   ```bash
   mkdir -p app/src/main/res/values-fr
   cp app/src/main/res/values/strings.xml app/src/main/res/values-fr/strings.xml
   ```

3. **Translate** each string's text. Leave the `name="…"` attributes alone.
   - **Delete** every entry marked `translatable="false"` (product names, technical text). They must not appear in a translation.
   - Keep the **format arguments**: `%1$s` (text) and `%1$d` (a whole number). Move them wherever your grammar needs them, but don't add, drop or change their type: `Created %1$s` might become `%1$s creado`. Write a literal percent sign as `%%` in strings that have arguments.
   - **Plurals:** translate each `<item quantity="…">` and add the quantities your language needs (`zero`, `one`, `two`, `few`, `many`, `other`; see the [CLDR plural rules](https://www.unicode.org/cldr/charts/latest/supplemental/language_plural_rules.html)). `other` is required. A singular form may drop the number (`<item quantity="one">one task</item>`).
   - **Escape** apostrophes and quotes as `\'` and `\"`, and `&` as `&amp;`. Keep `…` and `—` as they are.
   - Keep it short: most strings are buttons, tabs and labels on a phone screen. Where English uses Title Case, follow your language's usual capitalisation instead.
   - Keep names such as Paperclip, Cloudflare Access, Claude, Codex and model names untranslated. Translate ordinary words (tasks, agents, runs, approvals) the way people who use such tools in your language would say them.
   - Comments (`<!-- … -->`) explain context; you can leave them in English or remove them.
4. **Register the language** in `app/src/main/res/xml/locales_config.xml`, using a dash for the region:

   ```xml
   <locale android:name="fr" />
   ```

   That's all the picker needs: Settings → Language lists every language in this file, by its own name ("Français").
5. **Check it** (see below), then **open a pull request** with a title such as "Add French translation". Mention anything you weren't sure how to translate, and add a screenshot or two if you can.

A partial translation is fine to start with: any string you leave out shows in English. Remove untranslated entries rather than leaving English copies in your file, so the check can list what's still missing.

## Improve an existing translation

Edit the strings in `values-<lang>/strings.xml` and open a pull request. When new English strings have been added, the check below lists the keys your language is missing; copy those entries from `values/strings.xml` and translate them.

## Check your work

You need JDK 17 and the Android SDK (see the [README](../README.md#building)). Then:

```bash
./gradlew :app:testDebugUnitTest --tests com.bu.paperclip.TranslationsTest   # keys, format arguments, locales_config
./gradlew :app:lintDebug                                                       # XML and resource problems
./gradlew :app:assembleDebug                                                   # build the APK to try it
```

`TranslationsTest` **fails** when a translation:

- has a key that isn't in `values/strings.xml` (a typo, or a string that was renamed or removed),
- translates a `translatable="false"` entry,
- has different format arguments from the English (`%1$s` missing, or `%1$d` where English has `%1$s`),
- is in a `values-<lang>` folder that isn't listed in `locales_config.xml`, or the other way round.

Missing keys are only a **warning** (a `warning: values-<lang> is missing N key(s)` line in the output), because Android falls back to English. Lint reports them as `MissingTranslation` warnings.

To try it on a phone or emulator, install the APK (`adb install -r app/build/outputs/apk/debug/app-debug.apk`), open **Settings → Language** and pick yours. The [demo server](../tools/demo-server) gives you something to look at without a Paperclip account; see [DEVELOPMENT.md](DEVELOPMENT.md#running-against-the-demo-server). Watch for text that gets cut off or wraps badly on narrow screens.

## What isn't translated

Everything people and agents write (task titles, comments, documents, run transcripts) and names from the server (agents, projects, companies) are shown as written. Some server values, such as unusual status names, appear as the server sends them when the app doesn't know them.
