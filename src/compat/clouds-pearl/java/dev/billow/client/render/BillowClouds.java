package dev.billow.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.billow.client.BillowClient;
import dev.billow.config.BillowConfig;
import dev.billow.core.CellMap;
import dev.billow.core.Geometry;
import dev.billow.core.Sky;
import dev.billow.core.TileKey;
import dev.billow.core.TileSelector;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.DynamicGpuData;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.oit.OitPipelineSet;
import net.minecraft.client.renderer.oit.OitRenderPassProvider;
import net.minecraft.client.renderer.oit.OitStage;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public final class BillowClouds {
    private static final Logger LOGGER = LogManager.getLogger("billow");
    private static final Identifier VERTEX_SHADER = Identifier.fromNamespaceAndPath("billow", "core/clouds");
    private static final double BLOCKS_PER_TICK = 0.03;
    private static final double Z_OFFSET = 3.96;
    private static final int MAX_IN_FLIGHT = 6;
    private static final long UPLOAD_BYTES_PER_FRAME = 6L << 20;
    private static final int EVICT_AFTER_FRAMES = 900;

    private static final RenderPipeline PIPELINE = RenderPipeline.builder()
            .withBindGroupLayout(BindGroupLayouts.GLOBALS)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withBindGroupLayout(BindGroupLayouts.FOG)
            .withLocation(Identifier.fromNamespaceAndPath("billow", "pipeline/clouds"))
            .withVertexShader(VERTEX_SHADER)
            .withFragmentShader("core/clouds")
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .build();
    private static final OitPipelineSet OIT_PIPELINES = OitPipelineSet.builder("billow_clouds",
                    RenderPipeline.builder()
                            .withVertexShader(VERTEX_SHADER)
                            .withFragmentShader("core/clouds")
                            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
                            .withPrimitiveTopology(PrimitiveTopology.QUADS)
                            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                            .withBindGroupLayout(BindGroupLayouts.FOG))
            .withDepthBoundsModifier(builder -> builder.withDepthStencilState(DepthStencilState.DEFAULT))
            .build();

    private static final Identifier GAS_SHADER = Identifier.fromNamespaceAndPath("billow", "core/gas");
    private static final RenderPipeline GAS_PIPELINE = RenderPipeline.builder()
            .withBindGroupLayout(BindGroupLayouts.GLOBALS)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withBindGroupLayout(BindGroupLayouts.FOG)
            .withLocation(Identifier.fromNamespaceAndPath("billow", "pipeline/gas"))
            .withVertexShader(GAS_SHADER)
            .withFragmentShader(GAS_SHADER)
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withCull(false)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .build();
    private static final OitPipelineSet GAS_OIT_PIPELINES = OitPipelineSet.builder("billow_gas",
                    RenderPipeline.builder()
                            .withVertexShader(GAS_SHADER)
                            .withFragmentShader(GAS_SHADER)
                            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
                            .withPrimitiveTopology(PrimitiveTopology.QUADS)
                            .withCull(false)
                            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                            .withBindGroupLayout(BindGroupLayouts.FOG))
            .build();
    private static final double GAS_WRAP = 24576.0;

    private static final BillowClouds INSTANCE = new BillowClouds();

    private final ExecutorService workers = Executors.newFixedThreadPool(
            Math.max(1, Math.min(3, Runtime.getRuntime().availableProcessors() / 3)), runnable -> {
                Thread thread = new Thread(runnable, "Billow cloud mesher");
                thread.setDaemon(true);
                thread.setPriority(Thread.MIN_PRIORITY + 1);
                return thread;
            });
    private final Map<TileKey, Tile> tiles = new HashMap<>();
    private final ConcurrentLinkedQueue<Built> built = new ConcurrentLinkedQueue<>();
    private final AtomicInteger inFlight = new AtomicInteger();
    private final RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
    private final List<Draw> draws = new ArrayList<>();

    private Sky sky;
    private Sky.Settings settings;
    private Object texture;
    private int generation;
    private long frame;
    private Vector4f color = new Vector4f(1, 1, 1, 1);
    private final Matrix4f gasInfo = new Matrix4f();
    private boolean active;
    private int lastQuads;
    private int waiting;

    private static final class Tile {
        GpuBuffer buffer;
        int quads;
        boolean ready;
        boolean queued;
        long used;
    }

    private record Built(int generation, TileKey key, ByteBuffer data, int quads) {
    }

    private record Draw(Tile tile, Vector3f offset) {
    }

    private BillowClouds() {
    }

    public static BillowClouds get() {
        return INSTANCE;
    }

    public boolean active() {
        return active;
    }

    public void deactivate() {
        active = false;
    }

    public void prepare(Object textureKey, boolean[] cells, int width, int height, int argb, float bottomY,
                        int rangeChunks, Vec3 camera, long gameTime, float partialTicks) {
        active = true;
        BillowConfig config = BillowClient.config();
        Sky.Settings current = BillowClient.skySettings();
        if (sky == null || !current.equals(settings) || textureKey != texture) {
            reset();
            settings = current;
            texture = textureKey;
            CellMap map = cells != null ? CellMap.texture(cells, width, height) : CellMap.EMPTY;
            sky = new Sky(current, map);
        }
        frame++;
        upload();

        double ticks = (gameTime + (double) partialTicks) * config.speed() / 100.0;
        double cloudX = camera.x + ticks * BLOCKS_PER_TICK;
        double cloudZ = camera.z + Z_OFFSET;
        double radius = rangeChunks * 16.0;

        TileSelector.Result result = TileSelector.select(sky, cloudX, cloudZ, radius, this::ready);
        waiting = 0;
        for (TileKey key : result.wanted()) {
            Tile tile = tiles.computeIfAbsent(key, k -> new Tile());
            tile.used = frame;
            if (!tile.ready) {
                waiting++;
            }
            if (!tile.queued && inFlight.get() < MAX_IN_FLIGHT) {
                tile.queued = true;
                submit(key);
            }
        }

        draws.clear();
        float relativeY = (float) (bottomY - camera.y);
        int maxQuads = 0;
        for (TileKey key : result.draw()) {
            Tile tile = tiles.get(key);
            if (tile == null || !tile.ready) {
                continue;
            }
            tile.used = frame;
            if (tile.quads == 0) {
                continue;
            }
            int size = sky.tileSize(key.level());
            float offsetX = (float) ((double) key.x() * size - cloudX);
            float offsetZ = (float) ((double) key.z() * size - cloudZ);
            draws.add(new Draw(tile, new Vector3f(offsetX, relativeY, offsetZ)));
            maxQuads = Math.max(maxQuads, tile.quads);
        }
        lastQuads = maxQuads;
        if (maxQuads > 0) {
            indices.requestIndexCount(6 * maxQuads);
        }

        color = ARGB.vector4fFromARGB32(argb);
        gasInfo.identity()
                .m30((float) (cloudX - Math.floor(cloudX / GAS_WRAP) * GAS_WRAP))
                .m31((float) (camera.y - bottomY))
                .m32((float) (cloudZ - Math.floor(cloudZ / GAS_WRAP) * GAS_WRAP))
                .m33((float) (((gameTime % 240000L) + partialTicks) / 20.0));
        color.w *= config.opacity() / 100f;

        if (frame % 60 == 0) {
            evict();
        }
    }

    private boolean ready(TileKey key) {
        Tile tile = tiles.get(key);
        return tile != null && tile.ready;
    }

    private void submit(TileKey key) {
        Sky sky = this.sky;
        int generation = this.generation;
        inFlight.incrementAndGet();
        workers.execute(() -> {
            try {
                Geometry mesh = sky.mesh(key);
                built.add(new Built(generation, key, mesh.isEmpty() ? null : mesh.toBuffer(), mesh.quads()));
            } catch (Throwable e) {
                LOGGER.error("Could not build cloud tile {}", key, e);
                built.add(new Built(generation, key, null, 0));
            } finally {
                inFlight.decrementAndGet();
            }
        });
    }

    private void upload() {
        long budget = UPLOAD_BYTES_PER_FRAME;
        Built next;
        while ((next = built.peek()) != null) {
            if (next.generation() != generation) {
                built.poll();
                continue;
            }
            long bytes = next.data() == null ? 0 : next.data().remaining();
            if (bytes > budget && budget < UPLOAD_BYTES_PER_FRAME) {
                return;
            }
            built.poll();
            budget -= bytes;
            Tile tile = tiles.get(next.key());
            if (tile == null) {
                continue;
            }
            if (tile.buffer != null) {
                tile.buffer.close();
                tile.buffer = null;
            }
            if (next.data() != null) {
                String label = "Billow cloud tile " + next.key();
                tile.buffer = RenderSystem.getDevice().createBuffer(() -> label,
                        GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, next.data());
            }
            tile.quads = next.quads();
            tile.ready = true;
        }
    }

    private void evict() {
        Iterator<Map.Entry<TileKey, Tile>> it = tiles.entrySet().iterator();
        while (it.hasNext()) {
            Tile tile = it.next().getValue();
            if (frame - tile.used > EVICT_AFTER_FRAMES && (tile.ready || !tile.queued)) {
                if (tile.buffer != null) {
                    tile.buffer.close();
                }
                it.remove();
            }
        }
    }

    private void reset() {
        generation++;
        for (Tile tile : tiles.values()) {
            if (tile.buffer != null) {
                tile.buffer.close();
            }
        }
        tiles.clear();
        draws.clear();
    }

    public void render(RenderPass renderPass) {
        if (draws.isEmpty()) {
            return;
        }
        renderPass.pushDebugGroup(() -> "Billow clouds");
        RenderSystem.bindDefaultUniforms(renderPass);
        renderPass.setPipeline(RenderSystem.getCompiledPipeline(sky.gas() ? GAS_PIPELINE : PIPELINE));
        drawTiles(renderPass);
        renderPass.popDebugGroup();
    }

    public void renderOit(OitStage stage, GpuTextureView mainDepth, OitRenderPassProvider.Parameters params) {
        if (draws.isEmpty()) {
            return;
        }
        try (RenderPass renderPass = OitRenderPassProvider.createRenderPass(stage, () -> "Billow clouds", params)) {
            if (stage == OitStage.DEPTH_BOUNDS) {
                GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
                renderPass.setPipeline(RenderSystem.getCompiledPipeline(RenderPipelines.BLIT_DEPTH_DURING_DEPTH_BOUNDS));
                renderPass.setUniform("InSampler", mainDepth, nearest);
                renderPass.draw(3, 1, 0, 0);
            }
            OitPipelineSet pipelines = sky.gas() ? GAS_OIT_PIPELINES : OIT_PIPELINES;
            renderPass.setPipeline(RenderSystem.getCompiledPipeline(pipelines.getPipeline(stage)));
            drawTiles(renderPass);
        }
    }

    private void drawTiles(RenderPass renderPass) {
        Matrix4f modelView = RenderSystem.getModelViewMatrixCopy();
        DynamicGpuData.Transform[] transforms = new DynamicGpuData.Transform[draws.size()];
        for (int i = 0; i < transforms.length; i++) {
            transforms[i] = new DynamicGpuData.Transform(modelView, color, draws.get(i).offset(), gasInfo);
        }
        GpuBufferSlice[] slices = RenderSystem.getDynamicUniforms().writeTransforms(transforms);
        GpuBuffer indexBuffer = indices.getBuffer(6 * lastQuads);
        renderPass.setIndexBuffer(indexBuffer, indices.type());
        for (int i = 0; i < slices.length; i++) {
            Tile tile = draws.get(i).tile();
            renderPass.setUniform("DynamicTransforms", slices[i]);
            renderPass.setVertexBuffer(0, tile.buffer.slice());
            renderPass.drawIndexed(6 * tile.quads, 1, 0, 0, 0);
        }
    }

    public void close() {
        reset();
        sky = null;
        active = false;
    }

    public int drawnTiles() {
        return draws.size();
    }

    public long drawnQuads() {
        long total = 0;
        for (Draw draw : draws) {
            total += draw.tile().quads;
        }
        return total;
    }

    public boolean busy() {
        return waiting > 0 || inFlight.get() > 0 || !built.isEmpty();
    }
}
