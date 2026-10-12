package org.jarsi.devicewatch.data

/**
 * Kernel reads in the shape the app's full read command ([KernelCommands.FULL_READ])
 * prints them. The Pixel 8a one was captured under root on the phone; the smaller
 * ones are written by hand for the layouts no test phone has. Sysfs values only.
 */
internal object KernelFixtures {

    /** Pixel 8a (akita), Android 17, kernel 6.1.162, Magisk; read 2026-10-12, idle and cool. */
    val PIXEL_8A_FULL = """
/proc/sys/kernel/osrelease:6.1.162-android14-11-g9dd05ae3a1de-ab16261587
/sys/fs/selinux/enforce:1
/sys/devices/system/cpu/cpufreq/policy0/related_cpus:0 1 2 3
/sys/devices/system/cpu/cpufreq/policy4/related_cpus:4 5 6 7
/sys/devices/system/cpu/cpufreq/policy8/related_cpus:8
/sys/devices/system/cpu/cpufreq/policy0/cpuinfo_min_freq:324000
/sys/devices/system/cpu/cpufreq/policy4/cpuinfo_min_freq:402000
/sys/devices/system/cpu/cpufreq/policy8/cpuinfo_min_freq:500000
/sys/devices/system/cpu/cpufreq/policy0/cpuinfo_max_freq:1704000
/sys/devices/system/cpu/cpufreq/policy4/cpuinfo_max_freq:2367000
/sys/devices/system/cpu/cpufreq/policy8/cpuinfo_max_freq:2914000
/sys/devices/system/cpu/cpufreq/policy0/scaling_min_freq:324000
/sys/devices/system/cpu/cpufreq/policy4/scaling_min_freq:402000
/sys/devices/system/cpu/cpufreq/policy8/scaling_min_freq:500000
/sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq:1704000
/sys/devices/system/cpu/cpufreq/policy4/scaling_max_freq:2367000
/sys/devices/system/cpu/cpufreq/policy8/scaling_max_freq:2914000
/sys/devices/system/cpu/cpufreq/policy0/scaling_cur_freq:1197000
/sys/devices/system/cpu/cpufreq/policy4/scaling_cur_freq:402000
/sys/devices/system/cpu/cpufreq/policy8/scaling_cur_freq:500000
/sys/devices/system/cpu/cpufreq/policy0/scaling_governor:sched_pixel
/sys/devices/system/cpu/cpufreq/policy4/scaling_governor:sched_pixel
/sys/devices/system/cpu/cpufreq/policy8/scaling_governor:sched_pixel
/sys/devices/system/cpu/cpufreq/policy0/scaling_available_governors:sched_pixel conservative powersave performance schedutil 
/sys/devices/system/cpu/cpufreq/policy4/scaling_available_governors:sched_pixel conservative powersave performance schedutil 
/sys/devices/system/cpu/cpufreq/policy8/scaling_available_governors:sched_pixel conservative powersave performance schedutil 
/sys/devices/system/cpu/cpufreq/policy0/scaling_driver:exynos_cpufreq
/sys/devices/system/cpu/cpufreq/policy4/scaling_driver:exynos_cpufreq
/sys/devices/system/cpu/cpufreq/policy8/scaling_driver:exynos_cpufreq
/sys/devices/system/cpu/cpufreq/policy0/scaling_available_frequencies:324000 610000 820000 955000 1098000 1197000 1328000 1425000 1548000 1704000 
/sys/devices/system/cpu/cpufreq/policy4/scaling_available_frequencies:402000 578000 697000 712000 910000 1065000 1221000 1328000 1418000 1572000 1836000 1945000 2130000 2245000 2367000 
/sys/devices/system/cpu/cpufreq/policy8/scaling_available_frequencies:500000 880000 1164000 1298000 1557000 1745000 1885000 2049000 2147000 2294000 2363000 2556000 2687000 2850000 2914000 
/sys/devices/system/cpu/cpufreq/policy0/stats/time_in_state:324000 632941
/sys/devices/system/cpu/cpufreq/policy0/stats/time_in_state:610000 64119
/sys/devices/system/cpu/cpufreq/policy0/stats/time_in_state:820000 51335
/sys/devices/system/cpu/cpufreq/policy0/stats/time_in_state:955000 23127
/sys/devices/system/cpu/cpufreq/policy0/stats/time_in_state:1098000 16719
/sys/devices/system/cpu/cpufreq/policy0/stats/time_in_state:1197000 8118
/sys/devices/system/cpu/cpufreq/policy0/stats/time_in_state:1328000 8804
/sys/devices/system/cpu/cpufreq/policy0/stats/time_in_state:1425000 27446
/sys/devices/system/cpu/cpufreq/policy0/stats/time_in_state:1548000 6314
/sys/devices/system/cpu/cpufreq/policy0/stats/time_in_state:1704000 14984
/sys/devices/system/cpu/cpufreq/policy4/stats/time_in_state:402000 758435
/sys/devices/system/cpu/cpufreq/policy4/stats/time_in_state:578000 15345
/sys/devices/system/cpu/cpufreq/policy4/stats/time_in_state:697000 6066
/sys/devices/system/cpu/cpufreq/policy4/stats/time_in_state:712000 637
/sys/devices/system/cpu/cpufreq/policy4/stats/time_in_state:910000 8558
/sys/devices/system/cpu/cpufreq/policy4/stats/time_in_state:1065000 6924
/sys/devices/system/cpu/cpufreq/policy4/stats/time_in_state:1221000 3916
/sys/devices/system/cpu/cpufreq/policy4/stats/time_in_state:1328000 2640
/sys/devices/system/cpu/cpufreq/policy4/stats/time_in_state:1418000 1967
/sys/devices/system/cpu/cpufreq/policy4/stats/time_in_state:1572000 3217
/sys/devices/system/cpu/cpufreq/policy4/stats/time_in_state:1836000 22912
/sys/devices/system/cpu/cpufreq/policy4/stats/time_in_state:1945000 3061
/sys/devices/system/cpu/cpufreq/policy4/stats/time_in_state:2130000 3940
/sys/devices/system/cpu/cpufreq/policy4/stats/time_in_state:2245000 2708
/sys/devices/system/cpu/cpufreq/policy4/stats/time_in_state:2367000 13577
/sys/devices/system/cpu/cpufreq/policy8/stats/time_in_state:500000 817382
/sys/devices/system/cpu/cpufreq/policy8/stats/time_in_state:880000 3796
/sys/devices/system/cpu/cpufreq/policy8/stats/time_in_state:1164000 14851
/sys/devices/system/cpu/cpufreq/policy8/stats/time_in_state:1298000 777
/sys/devices/system/cpu/cpufreq/policy8/stats/time_in_state:1557000 2207
/sys/devices/system/cpu/cpufreq/policy8/stats/time_in_state:1745000 623
/sys/devices/system/cpu/cpufreq/policy8/stats/time_in_state:1885000 509
/sys/devices/system/cpu/cpufreq/policy8/stats/time_in_state:2049000 6208
/sys/devices/system/cpu/cpufreq/policy8/stats/time_in_state:2147000 923
/sys/devices/system/cpu/cpufreq/policy8/stats/time_in_state:2294000 1177
/sys/devices/system/cpu/cpufreq/policy8/stats/time_in_state:2363000 99
/sys/devices/system/cpu/cpufreq/policy8/stats/time_in_state:2556000 338
/sys/devices/system/cpu/cpufreq/policy8/stats/time_in_state:2687000 219
/sys/devices/system/cpu/cpufreq/policy8/stats/time_in_state:2850000 410
/sys/devices/system/cpu/cpufreq/policy8/stats/time_in_state:2914000 4386
/sys/class/thermal/thermal_zone0/type:BIG
/sys/class/thermal/thermal_zone1/type:MID
/sys/class/thermal/thermal_zone10/type:usb_conn_therm
/sys/class/thermal/thermal_zone11/type:soc_therm
/sys/class/thermal/thermal_zone12/type:charger_therm
/sys/class/thermal/thermal_zone13/type:display_therm
/sys/class/thermal/thermal_zone14/type:maxfg
/sys/class/thermal/thermal_zone15/type:gnss_tcxo_therm
/sys/class/thermal/thermal_zone16/type:battery
/sys/class/thermal/thermal_zone17/type:battery_cycle
/sys/class/thermal/thermal_zone18/type:soc
/sys/class/thermal/thermal_zone19/type:batt_vs
/sys/class/thermal/thermal_zone2/type:LITTLE
/sys/class/thermal/thermal_zone20/type:mdis_morepower
/sys/class/thermal/thermal_zone21/type:thb_hda
/sys/class/thermal/thermal_zone22/type:cp_on_chip_0
/sys/class/thermal/thermal_zone23/type:cp_on_chip_1
/sys/class/thermal/thermal_zone24/type:cp_on_chip_2
/sys/class/thermal/thermal_zone25/type:cp_on_chip_3
/sys/class/thermal/thermal_zone26/type:rf2_therm
/sys/class/thermal/thermal_zone27/type:rf1_therm
/sys/class/thermal/thermal_zone3/type:G3D
/sys/class/thermal/thermal_zone4/type:ISP
/sys/class/thermal/thermal_zone5/type:TPU
/sys/class/thermal/thermal_zone6/type:AUR
/sys/class/thermal/thermal_zone7/type:modem_therm
/sys/class/thermal/thermal_zone8/type:quiet_therm
/sys/class/thermal/thermal_zone9/type:rffe_therm
/sys/class/thermal/thermal_zone0/temp:26000
/sys/class/thermal/thermal_zone1/temp:26000
/sys/class/thermal/thermal_zone10/temp:24738
/sys/class/thermal/thermal_zone11/temp:25535
/sys/class/thermal/thermal_zone12/temp:25441
/sys/class/thermal/thermal_zone13/temp:24879
/sys/class/thermal/thermal_zone14/temp:24900
/sys/class/thermal/thermal_zone15/temp:25558
/sys/class/thermal/thermal_zone16/temp:24900
/sys/class/thermal/thermal_zone17/temp:411
/sys/class/thermal/thermal_zone18/temp:43
/sys/class/thermal/thermal_zone19/temp:24900
/sys/class/thermal/thermal_zone2/temp:26000
/sys/class/thermal/thermal_zone20/temp:0
/sys/class/thermal/thermal_zone21/temp:0
/sys/class/thermal/thermal_zone22/temp:29000
/sys/class/thermal/thermal_zone23/temp:30000
/sys/class/thermal/thermal_zone24/temp:29000
/sys/class/thermal/thermal_zone25/temp:29000
/sys/class/thermal/thermal_zone26/temp:23000
/sys/class/thermal/thermal_zone27/temp:23000
/sys/class/thermal/thermal_zone3/temp:26000
/sys/class/thermal/thermal_zone4/temp:25000
/sys/class/thermal/thermal_zone5/temp:26000
/sys/class/thermal/thermal_zone6/temp:26000
/sys/class/thermal/thermal_zone7/temp:25441
/sys/class/thermal/thermal_zone8/temp:25371
/sys/class/thermal/thermal_zone9/temp:25324
/sys/class/thermal/cooling_device0/type:BIG-tj
/sys/class/thermal/cooling_device1/type:MID-tj
/sys/class/thermal/cooling_device10/type:thermal-cpufreq-1
/sys/class/thermal/cooling_device11/type:thermal-cpufreq-2
/sys/class/thermal/cooling_device12/type:INTCAM
/sys/class/thermal/cooling_device13/type:DISP
/sys/class/thermal/cooling_device14/type:CAM
/sys/class/thermal/cooling_device15/type:TNR
/sys/class/thermal/cooling_device16/type:MFC
/sys/class/thermal/cooling_device17/type:BW
/sys/class/thermal/cooling_device18/type:DSU
/sys/class/thermal/cooling_device19/type:BCI
/sys/class/thermal/cooling_device2/type:LITTLE-tj
/sys/class/thermal/cooling_device20/type:usbc-port
/sys/class/thermal/cooling_device21/type:fcc
/sys/class/thermal/cooling_device22/type:dc_icl
/sys/class/thermal/cooling_device23/type:tpu_cooling
/sys/class/thermal/cooling_device24/type:thermal-gpufreq-0
/sys/class/thermal/cooling_device25/type:gxp-cooling
/sys/class/thermal/cooling_device3/type:G3D-tj
/sys/class/thermal/cooling_device4/type:ISP-tj
/sys/class/thermal/cooling_device5/type:TPU-tj
/sys/class/thermal/cooling_device6/type:AUR-tj
/sys/class/thermal/cooling_device7/type:MIF
/sys/class/thermal/cooling_device8/type:thermal-cpufreq-0
/sys/class/thermal/cooling_device9/type:INT
/sys/class/thermal/cooling_device0/cur_state:0
/sys/class/thermal/cooling_device1/cur_state:0
/sys/class/thermal/cooling_device10/cur_state:0
/sys/class/thermal/cooling_device11/cur_state:0
/sys/class/thermal/cooling_device12/cur_state:0
/sys/class/thermal/cooling_device13/cur_state:0
/sys/class/thermal/cooling_device14/cur_state:0
/sys/class/thermal/cooling_device15/cur_state:0
/sys/class/thermal/cooling_device16/cur_state:0
/sys/class/thermal/cooling_device17/cur_state:0
/sys/class/thermal/cooling_device18/cur_state:0
/sys/class/thermal/cooling_device19/cur_state:0
/sys/class/thermal/cooling_device2/cur_state:0
/sys/class/thermal/cooling_device20/cur_state:0
/sys/class/thermal/cooling_device21/cur_state:0
/sys/class/thermal/cooling_device22/cur_state:0
/sys/class/thermal/cooling_device23/cur_state:0
/sys/class/thermal/cooling_device24/cur_state:0
/sys/class/thermal/cooling_device25/cur_state:0
/sys/class/thermal/cooling_device3/cur_state:0
/sys/class/thermal/cooling_device4/cur_state:0
/sys/class/thermal/cooling_device5/cur_state:0
/sys/class/thermal/cooling_device6/cur_state:0
/sys/class/thermal/cooling_device7/cur_state:0
/sys/class/thermal/cooling_device8/cur_state:0
/sys/class/thermal/cooling_device9/cur_state:0
/sys/class/thermal/cooling_device0/max_state:0
/sys/class/thermal/cooling_device1/max_state:0
/sys/class/thermal/cooling_device10/max_state:14
/sys/class/thermal/cooling_device11/max_state:14
/sys/class/thermal/cooling_device12/max_state:6
/sys/class/thermal/cooling_device13/max_state:7
/sys/class/thermal/cooling_device14/max_state:6
/sys/class/thermal/cooling_device15/max_state:6
/sys/class/thermal/cooling_device16/max_state:7
/sys/class/thermal/cooling_device17/max_state:6
/sys/class/thermal/cooling_device18/max_state:11
/sys/class/thermal/cooling_device19/max_state:8
/sys/class/thermal/cooling_device2/max_state:0
/sys/class/thermal/cooling_device20/max_state:1
/sys/class/thermal/cooling_device21/max_state:26
/sys/class/thermal/cooling_device22/max_state:26
/sys/class/thermal/cooling_device23/max_state:7
/sys/class/thermal/cooling_device24/max_state:12
/sys/class/thermal/cooling_device25/max_state:6
/sys/class/thermal/cooling_device3/max_state:0
/sys/class/thermal/cooling_device4/max_state:0
/sys/class/thermal/cooling_device5/max_state:0
/sys/class/thermal/cooling_device6/max_state:0
/sys/class/thermal/cooling_device7/max_state:13
/sys/class/thermal/cooling_device8/max_state:9
/sys/class/thermal/cooling_device9/max_state:7
/sys/class/misc/mali0/device/cur_freq:150000
/sys/class/misc/mali0/device/max_freq:890000
/sys/class/misc/mali0/device/min_freq:150000
/sys/class/misc/mali0/device/available_frequencies:890000 850000 807000 723000 649000 580000 521000 467000 419000 376000 337000 302000 150000 
/sys/class/misc/mali0/device/power_policy:coarse_demand [adaptive] always_on 
/sys/block/zram0/disksize:3970453504
/sys/block/zram0/comp_algorithm:zstd lz4 lzo-rle lzo [lz77eh] 
/sys/block/zram0/mm_stat:2486063104 694901841 727855104        0 869654528    16722   119399    18837    63320
/sys/block/sda/queue/scheduler:[mq-deadline] kyber bfq none
/sys/block/sdb/queue/scheduler:[mq-deadline] kyber bfq none
/sys/block/sdc/queue/scheduler:[mq-deadline] kyber bfq none
/sys/block/sdd/queue/scheduler:[mq-deadline] kyber bfq none
""".trim()

    /** Two clusters, one with no frequency list: its steps come from time_in_state. */
    val TWO_CLUSTERS_TIME_IN_STATE = """
/sys/devices/system/cpu/cpufreq/policy0/related_cpus:0 1 2 3 4 5
/sys/devices/system/cpu/cpufreq/policy0/cpuinfo_min_freq:300000
/sys/devices/system/cpu/cpufreq/policy0/cpuinfo_max_freq:1800000
/sys/devices/system/cpu/cpufreq/policy0/scaling_min_freq:300000
/sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq:1800000
/sys/devices/system/cpu/cpufreq/policy0/scaling_cur_freq:576000
/sys/devices/system/cpu/cpufreq/policy0/scaling_governor:schedutil
/sys/devices/system/cpu/cpufreq/policy0/scaling_available_frequencies:300000 576000 1017600 1324800 1800000
/sys/devices/system/cpu/cpufreq/policy6/related_cpus:6 7
/sys/devices/system/cpu/cpufreq/policy6/cpuinfo_min_freq:652800
/sys/devices/system/cpu/cpufreq/policy6/cpuinfo_max_freq:2400000
/sys/devices/system/cpu/cpufreq/policy6/scaling_min_freq:652800
/sys/devices/system/cpu/cpufreq/policy6/scaling_max_freq:2400000
/sys/devices/system/cpu/cpufreq/policy6/scaling_cur_freq:652800
/sys/devices/system/cpu/cpufreq/policy6/scaling_governor:schedutil
/sys/devices/system/cpu/cpufreq/policy6/stats/time_in_state:652800 1200
/sys/devices/system/cpu/cpufreq/policy6/stats/time_in_state:1209600 300
/sys/devices/system/cpu/cpufreq/policy6/stats/time_in_state:1708800 80
/sys/devices/system/cpu/cpufreq/policy6/stats/time_in_state:2400000 20
/sys/class/kgsl/kgsl-3d0/gpuclk:257000000
/sys/class/kgsl/kgsl-3d0/max_gpuclk:840000000
/sys/class/kgsl/kgsl-3d0/gpu_available_frequencies:840000000 670000000 490000000 257000000
""".trim()

    /** One cluster whose kernel lists no frequencies at all and keeps no statistics. */
    val ONE_CLUSTER_NO_STEPS = """
/sys/devices/system/cpu/cpufreq/policy0/related_cpus:0 1 2 3
/sys/devices/system/cpu/cpufreq/policy0/cpuinfo_min_freq:400000
/sys/devices/system/cpu/cpufreq/policy0/cpuinfo_max_freq:2000000
/sys/devices/system/cpu/cpufreq/policy0/scaling_min_freq:400000
/sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq:2000000
/sys/devices/system/cpu/cpufreq/policy0/scaling_governor:schedutil
""".trim()
}
