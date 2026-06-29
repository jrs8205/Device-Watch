# Project-specific R8 rules.
# AndroidX Compose, Glance, DataStore, and app widget classes are covered by
# library consumer rules. Keep this file intentionally small unless a release
# build reports a concrete missing keep rule.

# WorkManager initializes its internal Room database through reflection. Keep
# the generated implementation constructor so R8 does not remove it in release.
-keep class androidx.work.impl.WorkDatabase_Impl {
    <init>();
}
