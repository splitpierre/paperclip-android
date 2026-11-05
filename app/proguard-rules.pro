# Serialization serializers are referenced by generated code. Library consumer rules
# retain the runtime pieces; keep generic signatures used by adapters.
-keepattributes Signature,InnerClasses,EnclosingMethod
# Markwon probes these optional decoders through SvgSupport/GifSupport before use.
# They are not bundled; the default bitmap decoder remains available.
-dontwarn com.caverock.androidsvg.SVG
-dontwarn com.caverock.androidsvg.SVGParseException
-dontwarn pl.droidsonroids.gif.GifDrawable
