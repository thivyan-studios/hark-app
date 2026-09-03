#!/system/bin/sh
HERE="$(cd "$(dirname "$0")" && pwd)"
export ASAN_OPTIONS=log_to_syslog=false,allow_user_segv_handler=1
ASAN_LIB=$(ls $HERE/libclang_rt.asan-*-android.so)
if [ -f "$ASAN_LIB" ]; then
    export LD_PRELOAD=$ASAN_LIB
fi
exec "$@"
