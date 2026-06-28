export function computeFloatingEditorPosition({
  nodeRect,
  viewport,
  panel,
  gap = 18,
  margin = 16,
}) {
  const candidates = [
    {
      placement: 'right',
      x: nodeRect.left + nodeRect.width + gap,
      y: nodeRect.top,
    },
    {
      placement: 'left',
      x: nodeRect.left - panel.width - gap,
      y: nodeRect.top,
    },
    {
      placement: 'bottom',
      x: nodeRect.left,
      y: nodeRect.top + nodeRect.height + gap,
    },
    {
      placement: 'top',
      x: nodeRect.left,
      y: nodeRect.top - panel.height - gap,
    },
  ]

  const fits = ({ x, y }) => (
    x >= margin
    && y >= margin
    && x + panel.width <= viewport.width - margin
    && y + panel.height <= viewport.height - margin
  )

  const selected = candidates.find(fits) || candidates[0]
  const maxX = Math.max(margin, viewport.width - panel.width - margin)
  const maxY = Math.max(margin, viewport.height - panel.height - margin)

  return {
    placement: selected.placement,
    x: Math.min(Math.max(selected.x, margin), maxX),
    y: Math.min(Math.max(selected.y, margin), maxY),
  }
}
