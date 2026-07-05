/**
 * Three.js 场景控制器（纯逻辑，不依赖 DOM）。
 * 管理 WebGLRenderer、OrbitControls、TransformControls、GLTFLoader 等生命周期。
 *
 * 桌面端 GLB 预算：
 *   - 单个 GLB 三角形上限：500,000
 *   - 场景总计三角形：2,000,000
 *   - 场景总计对象：500
 *   - 最大纹理分辨率：4096×4096
 *   - 最大 GLB 文件大小：200 MB
 */

export const SCENE_BUDGET = Object.freeze({
  maxTrianglesPerGlb: 500_000,
  maxTrianglesTotal: 2_000_000,
  maxObjectsTotal: 500,
  maxTextureSize: 4096,
  maxGlbFileSizeBytes: 200 * 1024 * 1024
})

/**
 * 创建一个测试用的 Three.js 资源集合（用于单元测试 mock）。
 */
export function fakeThreeResources() {
  return {
    renderer: { domElement: document.createElement('canvas'), render: () => {}, dispose: () => {}, setSize: () => {} },
    controls: { dispose: () => {}, update: () => {}, target: { set: () => {} } },
    scene: { add: () => {}, remove: () => {}, children: [], traverse: () => {} },
    camera: { position: { set: () => {} }, lookAt: () => {}, updateProjectionMatrix: () => {} },
    gltfLoader: { load: () => {} }
  }
}

/**
 * Three.js 场景控制器工厂。
 *
 * @param {{ canvas: HTMLCanvasElement, document: object, onChange: Function }} opts
 * @returns SceneController
 */
export function createSceneController({ canvas, document, onChange }) {
  let disposed = false
  let renderer = null
  let scene = null
  let camera = null
  let controls = null
  let animationId = null

  // 使用传入的 THREE 实例初始化（由调用方动态 import 后传入）
  function initThree(THREE, OrbitControls) {
    if (!THREE) return
    renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: true })
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
    renderer.shadowMap.enabled = true

    scene = new THREE.Scene()
    scene.background = new THREE.Color(0x060608)

    camera = new THREE.PerspectiveCamera(50, canvas.width / canvas.height, 0.1, 1000)
    camera.position.set(0, 2.2, 10)
    camera.lookAt(0, 1.2, 0)

    controls = new OrbitControls(camera, renderer.domElement)
    controls.enableDamping = true
    controls.target.set(0, 1.2, 0)
    controls.update()

    // 辅助元素
    scene.add(new THREE.GridHelper(20, 20))
    scene.add(new THREE.AmbientLight(0x404040, 0.5))
    const dirLight = new THREE.DirectionalLight(0xffffff, 1.0)
    dirLight.position.set(5, 10, 5)
    scene.add(dirLight)

    startLoop()
  }

  function startLoop() {
    function loop() {
      if (disposed) return
      animationId = requestAnimationFrame(loop)
      controls?.update()
      renderer?.render(scene, camera)
    }
    loop()
  }

  function dispose() {
    disposed = true
    if (animationId) cancelAnimationFrame(animationId)
    controls?.dispose()
    renderer?.dispose()
    // 释放 Geometry/Material
    if (scene) {
      scene.traverse(obj => {
        if (obj.geometry) obj.geometry.dispose()
        if (obj.material) {
          if (Array.isArray(obj.material)) obj.material.forEach(m => m.dispose())
          else obj.material.dispose()
        }
      })
    }
  }

  function checkBudget(glbData) {
    let triangles = 0
    if (glbData) {
      // 粗略估算三角形数（实际应解析 GLB buffer）
      triangles = glbData.triCount || 0
    }
    if (triangles > SCENE_BUDGET.maxTrianglesPerGlb) {
      return { allowed: false, reason: `单文件三角形数 ${triangles} 超过上限 ${SCENE_BUDGET.maxTrianglesPerGlb}` }
    }
    return { allowed: true }
  }

  function resize(width, height) {
    if (renderer && camera) {
      renderer.setSize(width, height)
      camera.aspect = width / height
      camera.updateProjectionMatrix()
    }
  }

  // caller must invoke init(THREE, OrbitControls) after dynamic import
  async function init(THREE, OrbitControls) {
    if (!THREE) return
    initThree(THREE, OrbitControls)
  }

  return {
    init,
    get renderer() { return renderer },
    get scene() { return scene },
    get camera() { return camera },
    get controls() { return controls },
    isReady: () => renderer !== null,
    resize,
    dispose,
    checkBudget,
    loadAsset: async (url) => { /* TODO: GLTFLoader.load */ },
    selectObject: (id) => { /* TODO */ },
    setTransformMode: (mode) => { /* TODO: TransformControls mode */ },
    renderFrame: (timeMs) => { /* TODO: set from timeline */ }
  }
}
