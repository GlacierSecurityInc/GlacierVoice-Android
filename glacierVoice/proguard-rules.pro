-dontwarn org.apache.**

-keep class androidx.core.app.CoreComponentFactory { *; }

-keep class com.glaciersecurity.glaciervoice.**

# Class names are needed in reflection
-keepnames class com.amazonaws.**
-keepnames class com.amazon.**
# Request handlers defined in request.handlers
-keep class com.amazonaws.services.**.*Handler

# The SDK has several references of Apache HTTP client
-dontwarn com.amazonaws.http.**
-dontwarn com.amazonaws.metrics.**
