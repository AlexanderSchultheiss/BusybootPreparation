# BusybootPreparation

![Build Status](https://jenkins-2.sse.uni-hildesheim.de/buildStatus/icon?job=KH_BusybootPreparation)

A utility plugin for [KernelHaven](https://github.com/KernelHaven/KernelHaven).

Utilities for preparing Busybox and Coreboot source trees for KernelHaven.

## Usage

Place [`BusybootPreparation.jar`](https://jenkins-2.sse.uni-hildesheim.de/job/KH_BusybootPreparation/lastSuccessfulBuild/artifact/build/jar/BusybootPreparation.jar) in the plugins folder of KernelHaven.

To use this preparation, set `preparation.class.0` to `net.ssehub.kernel_haven.busyboot.PrepareBusybox` or `net.ssehub.kernel_haven.busyboot.PrepareCoreboot` in the KernelHaven properties.

## Dependencies

This plugin has no additional dependencies other than KernelHaven.

## License

This plugin is licensed under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0.html).
