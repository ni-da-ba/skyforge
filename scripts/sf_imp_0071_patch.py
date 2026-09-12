from pathlib import Path

surface = Path('skyforge-neoforge-1211/src/main/java/io/github/nidaba/skyforge/neoforge1211/SkyforgeNeoForge1211SurfaceStage.java')
text = surface.read_text()
if 'import net.minecraft.server.level.ServerLevel;' not in text:
    text = text.replace(
        'import net.minecraft.server.level.WorldGenRegion;\n',
        'import net.minecraft.server.level.ServerLevel;\nimport net.minecraft.server.level.WorldGenRegion;\n',
        1)
anchor = '    static int serviceOneCatchup(ChunkAccess chunk) {\n'
insertion = '''    /** Advances one bounded persisted deferred-terrain packet for the canonical obligation. */
    static DeferredCatchupPacketResult serviceOneCatchupPacket(
            ServerLevel level,
            ChunkAccess chunk,
            int maximumAssignedSolidWrites) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(chunk, "chunk");
        if (maximumAssignedSolidWrites <= 0) {
            throw new IllegalArgumentException("maximumAssignedSolidWrites must be positive");
        }
        RuntimeBinding binding = ACTIVE.get();
        if (binding == null) {
            return new DeferredCatchupPacketResult(false, false, 0);
        }
        var progressData = SkyforgeDeferredTerrainWriteProgressData.get(level);
        for (var pending : SkyforgePhysicalVolumeAdmissionStage.eligibleCatchup(chunk.getPos())) {
            return realizeDeferredPacket(binding, chunk, pending, progressData, maximumAssignedSolidWrites);
        }
        return new DeferredCatchupPacketResult(false, false, 0);
    }

    private static DeferredCatchupPacketResult realizeDeferredPacket(
            RuntimeBinding binding,
            ChunkAccess chunk,
            SkyforgePhysicalVolumeAdmissionStage.PendingRealization pending,
            SkyforgeDeferredTerrainWriteProgressData progressData,
            int maximumAssignedSolidWrites) {
        long performanceStart = SkyforgeRuntimePerformanceMetrics.start();
        WorldBounds volumeBounds = binding.adapter().volumeBounds(pending.volumeId())
                .orElseThrow(() -> new IllegalStateException(
                        "deferred realization requires exact bound volume " + pending.volumeId().path()));
        VerticalRange range = boundedVerticalRange(chunk, volumeBounds);
        if (range.height() <= 0) {
            throw new IllegalStateException(
                    "deferred exact-volume realization has no vertical overlap with target chunk");
        }
        SkyforgeRuntimePerformanceMetrics.recordSample("terrain.deferredVerticalSamples", range.height());
        long materializeStart = SkyforgeRuntimePerformanceMetrics.start();
        MinecraftChunkMaterialization materialization = binding.adapter().materialize(
                pending.volumeId(), chunk.getPos(), range.minimumY(), range.height());
        SkyforgeRuntimePerformanceMetrics.recordSince("terrain.deferred.materialize", materializeStart);
        if (binding.nativeSurfaceTopAdapter().isPresent()) {
            MinecraftNativeSurfaceSnapshot snapshot = pending.nativeSurfaceSnapshot()
                    .orElseThrow(() -> new IllegalStateException(
                            "native-surface-adapted deferred realization lost its pre-decoration snapshot"));
            long adaptStart = SkyforgeRuntimePerformanceMetrics.start();
            materialization = binding.nativeSurfaceTopAdapter().orElseThrow().adapt(snapshot, materialization);
            SkyforgeRuntimePerformanceMetrics.recordSince("terrain.deferred.adaptSurface", adaptStart);
        }

        var existing = progressData.find(pending.volumeId(), pending.chunkKey());
        int expectedSolidBlocks;
        if (existing.isPresent()) {
            expectedSolidBlocks = existing.orElseThrow().expectedSolidBlocks();
        } else {
            long solidCountStart = SkyforgeRuntimePerformanceMetrics.start();
            expectedSolidBlocks = materialization.solidBlockCount();
            SkyforgeRuntimePerformanceMetrics.recordSince("terrain.deferred.solidCount", solidCountStart);
        }
        var progress = progressData.getOrCreate(pending.volumeId(), pending.chunkKey(), expectedSolidBlocks);
        if (progress.terminal()) {
            SkyforgePhysicalVolumeAdmissionStage.completeCatchup(pending);
            SkyforgeRuntimePerformanceMetrics.recordSince("terrain.realizeDeferredPacket", performanceStart);
            return new DeferredCatchupPacketResult(true, true, 0);
        }

        boolean exactAdmissionFastPath = SkyforgePhysicalVolumeAdmissionStage.canUseExactDeferredWriteFastPath(
                pending, chunk, range.minimumY(), range.height());
        SkyforgeRuntimePerformanceMetrics.recordSample(
                "terrain.deferredExactAdmissionFastPath", exactAdmissionFastPath ? 1L : 0L);
        long writeStart = SkyforgeRuntimePerformanceMetrics.start();
        var advance = binding.writer().writeDeferredSolidOverlayPacket(
                chunk,
                materialization,
                progress.cursor(),
                maximumAssignedSolidWrites,
                !exactAdmissionFastPath);
        SkyforgeRuntimePerformanceMetrics.recordSince("terrain.deferred.writePacket", writeStart);

        if (advance.complete()
                && advance.cursor().cumulativeAssignedSolidWrites() != expectedSolidBlocks) {
            throw new IllegalStateException(
                    "completed deferred terrain packet count differs from authoritative solid count");
        }
        boolean terminal = advance.complete();
        progressData.store(progress.advance(advance, terminal));
        if (terminal) {
            long completeStart = SkyforgeRuntimePerformanceMetrics.start();
            SkyforgePhysicalVolumeAdmissionStage.completeCatchup(pending);
            SkyforgeRuntimePerformanceMetrics.recordSince("terrain.deferred.completeCatchup", completeStart);
        }
        SkyforgeRuntimePerformanceMetrics.recordSince("terrain.realizeDeferredPacket", performanceStart);
        boolean worked = terminal || advance.assignedSolidWrites() > 0;
        return new DeferredCatchupPacketResult(worked, terminal, advance.assignedSolidWrites());
    }

    record DeferredCatchupPacketResult(boolean worked, boolean completed, int assignedSolidWrites) {
        DeferredCatchupPacketResult {
            if (assignedSolidWrites < 0) {
                throw new IllegalArgumentException("assignedSolidWrites must be nonnegative");
            }
            if (completed && !worked) {
                throw new IllegalArgumentException("completed packet must count as worked");
            }
        }
    }

'''
if 'serviceOneCatchupPacket(' not in text:
    if anchor not in text:
        raise SystemExit('SurfaceStage serviceOneCatchup anchor missing')
    text = text.replace(anchor, insertion + anchor, 1)
surface.write_text(text)

service = Path('skyforge-neoforge-1211/src/main/java/io/github/nidaba/skyforge/neoforge1211/SkyforgePhysicalVolumeCatchupService.java')
text = service.read_text()
const_anchor = '    static final int MAX_TERRAIN_CATCHUP_CHUNKS_PER_LEVEL_TICK = 64;\n'
if 'MAX_ASSIGNED_SOLID_WRITES_PER_TERRAIN_QUANTUM' not in text:
    if const_anchor not in text:
        raise SystemExit('CatchupService constant anchor missing')
    text = text.replace(
        const_anchor,
        const_anchor + '    static final int MAX_ASSIGNED_SOLID_WRITES_PER_TERRAIN_QUANTUM = 1024;\n',
        1)
old = '''            int completed;
            var mutationLifecycle = SkyforgeDeferredChunkMutationLifecycle.open(level, chunk);
            try {
                completed = SkyforgeNeoForge1211SurfaceStage.serviceOneCatchup(chunk);
            } finally {
                mutationLifecycle.close();
            }
            if (completed <= 0) {
                return false;
            }
            if (SkyforgePhysicalVolumeAdmissionStage.eligibleCatchup(chunk.getPos()).isEmpty()) {
                SkyforgeNativeSurfacePopulationStage.populateDeferred(level, chunk, generator);
            }
            return true;
'''
new = '''            SkyforgeNeoForge1211SurfaceStage.DeferredCatchupPacketResult packet;
            var mutationLifecycle = SkyforgeDeferredChunkMutationLifecycle.open(level, chunk);
            try {
                packet = SkyforgeNeoForge1211SurfaceStage.serviceOneCatchupPacket(
                        level,
                        chunk,
                        MAX_ASSIGNED_SOLID_WRITES_PER_TERRAIN_QUANTUM);
            } finally {
                mutationLifecycle.close();
            }
            if (!packet.worked()) {
                return false;
            }
            if (packet.assignedSolidWrites() > MAX_ASSIGNED_SOLID_WRITES_PER_TERRAIN_QUANTUM) {
                throw new IllegalStateException("deferred terrain packet exceeded scheduler write budget");
            }
            if (packet.completed()
                    && SkyforgePhysicalVolumeAdmissionStage.eligibleCatchup(chunk.getPos()).isEmpty()) {
                SkyforgeNativeSurfacePopulationStage.populateDeferred(level, chunk, generator);
            }
            return true;
'''
if 'serviceOneCatchupPacket(' not in text:
    if old not in text:
        raise SystemExit('CatchupService scheduler anchor missing')
    text = text.replace(old, new, 1)
service.write_text(text)
