#!/bin/sh
# M.I.B. -> Advanced Settings -> Run Custom Script: CarPlay cluster ETA ON.
#
# Asks iOS to draw its trip summary (arrival time, time left, distance left) in the
# AltScreen cluster video. The hook then requests the cluster UI as
#   maps:/car/instrumentcluster?showSpeedLimit=user&showCompass=user&showETA=yes&maneuverLayout=
# instead of the bare maps:/car/instrumentcluster (active_alt_url_copy() in
# altscreen_hook/libaltscreen111_gen2.c). Useful with apps that send no CarPlay route
# guidance (Waze), where the RGI overlay has no ETA to show. With Apple Maps / Google
# Maps the ETA then appears twice (iOS summary + RGI overlay).
#
# Writes /mnt/app/root/mibr-carplay111-nav.showETA (yes) and then the master switch
# /mnt/app/root/mibr-carplay111-nav-query.enabled. Applies from the next phone connect,
# or in the running session with GEM "Re-request cluster map". Idempotent.
#
# QNX 6.5 /bin/sh is ksh; stays inside its portable subset.
# SPDX-License-Identifier: GPL-3.0-or-later
set -u
PATH=/proc/boot:/bin:/usr/bin:/usr/sbin:/sbin:/mnt/app/armle/bin:/mnt/app/armle/usr/bin
export PATH
unset LD_PRELOAD

case $0 in */*) D=${0%/*} ;; *) D=. ;; esac
D=$(cd "$D" && pwd) || exit 1

if [ ! -d /mnt/app/eso/hmi/lsd ] && [ -d /net/mmx/mnt/app/eso/hmi/lsd ]; then
    exec on -f mmx /bin/sh "$D/custom.sh" "$@"
fi

R=${CP_NAV_ROOT:-/mnt/app/root}
M=$R/mibr-carplay111-nav-query.enabled
E=$R/mibr-carplay111-nav.showETA
echo "custom.sh: CarPlay cluster ETA ON"
mount -uw /mnt/app 2>/dev/null || echo "custom.sh: mount -uw /mnt/app failed"
echo yes > "$E" || { echo "FAILED: cannot write $E (is /mnt/app read-only?)"; exit 1; }
: > "$M" || { echo "FAILED: cannot create $M (is /mnt/app read-only?)"; exit 1; }
sync
echo "DONE. Cluster ETA ON (showETA=yes)."
echo "Reconnect the phone, or press 'Re-request cluster map' during a CarPlay session."
