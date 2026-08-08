function asArray(value) {
  return Array.isArray(value) ? value : []
}

function text(value) {
  return value == null ? '' : String(value)
}

/** Keeps the server-projected Markdown intact for trusted previews. */
export function normalizeSceneAssetMarkdown(raw) {
  const source = raw?.data ?? raw ?? {}
  return { path: text(source.path), content: text(source.content), source: 'server' }
}

/** Draft-only preview: it has no upload URL and never implies persistence succeeded. */
export function buildSceneAssetMarkdownPreview(asset = {}) {
  const master = asset.master ?? asset
  const lines = [`# ${text(asset.name || master.name || '未命名场景')}`, '', '## 主场景设定', '']
  for (const [key, value] of Object.entries(master)) {
    if (['id', 'stableId', 'variants'].includes(key) || value == null || value === '') continue
    lines.push(`- ${key}: ${Array.isArray(value) ? value.join(', ') : text(value)}`)
  }
  lines.push('', '## 场景变体', '')
  const variants = asArray(asset.variants)
  if (!variants.length) lines.push('- 无')
  for (const variant of variants) lines.push(`- ${text(variant.id)} · ${text(variant.name)}`)
  return { path: '', content: lines.join('\n'), source: 'local-draft' }
}

export const sceneAssetMarkdownPreview = buildSceneAssetMarkdownPreview
