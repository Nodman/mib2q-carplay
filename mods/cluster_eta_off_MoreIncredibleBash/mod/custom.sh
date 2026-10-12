#!/bin/sh
# M.I.B. -> Advanced Settings -> Run Custom Script: CarPlay cluster ETA OFF (default).
#
# Removes /mnt/app/root/mibr-carplay111-nav-query.enabled and
# /mnt/app/root/mibr-carplay111-nav.showETA, so the hook requests the bare
# maps:/car/instrumentcluster again (stock behaviour, no iOS trip summary in the
# cluster video). Applies from the next phone connect, or in the running session with
# GEM "Re-request cluster map". Idempotent: a no-op when the ETA is already off.
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
echo "custom.sh: CarPlay cluster ETA OFF"
mount -uw /mnt/app 2>/dev/null || echo "custom.sh: mount -uw /mnt/app failed"
rm -f "$M" "$E"
if [ -e "$M" ]; then
    echo "FAILED: $M still present (is /mnt/app read-only?)"
    exit 1
fi
sync
echo "DONE. Cluster ETA OFF (bare maps:/car/instrumentcluster)."
echo "Reconnect the phone, or press 'Re-request cluster map' during a CarPlay session."
