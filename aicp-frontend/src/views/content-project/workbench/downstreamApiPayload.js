function snakeKey(key) {
  return key.replace(/[A-Z]/g, letter => `_${letter.toLowerCase()}`)
}

/** Workbench state is camelCase; backend storyboard DTOs are snake_case at this boundary. */
export function toContentProjectPayload(value) {
  if (Array.isArray(value)) return value.map(toContentProjectPayload)
  if (value === null || typeof value !== 'object') return value
  return Object.fromEntries(Object.entries(value).map(([key, item]) => [snakeKey(key), toContentProjectPayload(item)]))
}
