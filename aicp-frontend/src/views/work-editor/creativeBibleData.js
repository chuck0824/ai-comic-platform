// Mode-adaptive section list
export function sectionsForMode(mode) {
  const base = ['overview', 'ecosystem', 'characters', 'writing']
  if (mode === 'long_form') return ['overview', 'ecosystem', 'characters', 'relations', 'writing', 'continuity']
  if (mode === 'short_drama') return ['overview', 'ecosystem', 'characters', 'relations', 'writing']
  if (mode === 'tvc') return ['overview', 'ecosystem', 'characters', 'writing']
  return base
}

// Fields that cannot be overridden by character or unit guides
export const NON_OVERRIDABLE = ['hard_bans', 'platform_rules', 'compliance_rules']

// Merge project guide with an optional unit-level override.
// Returns { resolved, conflicts } where conflicts lists fields blocked from override.
export function mergeWritingGuide(projectGuide, unitGuide) {
  const resolved = { ...(projectGuide || {}) }
  const conflicts = []

  if (unitGuide) {
    for (const [key, value] of Object.entries(unitGuide)) {
      if (NON_OVERRIDABLE.includes(key)) {
        if (JSON.stringify(resolved[key]) !== JSON.stringify(value)) {
          conflicts.push(key)
        }
        // keep project-level value for non-overridable fields
      } else {
        resolved[key] = value
      }
    }
  }

  return { resolved, conflicts }
}

// Normalize bible health response; safe for null/undefined input
export function normalizeBibleHealth(raw) {
  if (!raw) {
    return {
      status: 'missing',
      current_version_id: 0,
      current_version_no: 0,
      confirmed_fact_count: 0,
      pending_change_count: 0,
      ready_for_generation: false
    }
  }
  return {
    status: raw.status ?? 'missing',
    current_version_id: raw.current_version_id ?? 0,
    current_version_no: raw.current_version_no ?? 0,
    confirmed_fact_count: raw.confirmed_fact_count ?? 0,
    pending_change_count: raw.pending_change_count ?? 0,
    ready_for_generation: raw.ready_for_generation ?? false
  }
}

// Ecosystem rule type definitions
export const ECOSYSTEM_RULE_TYPES = [
  ['era_world', '时代与世界'],
  ['world_rule', '世界规则'],
  ['social_structure', '社会结构'],
  ['institution_taboo', '制度与禁忌'],
  ['faction_organization', '势力与组织'],
  ['resource_system', '资源体系'],
  ['ability_system', '能力体系'],
  ['location_system', '地点体系'],
  ['key_history', '关键历史']
]

// Writing guide field definitions per scope
export const PROJECT_FIELDS = [
  'pov', 'tense', 'pace', 'language_density', 'tone',
  'dialogue_ratio', 'hard_bans', 'terminology'
]

export const CHARACTER_FIELDS = [
  'addressing', 'sentence_length', 'favorite_words', 'catchphrases',
  'knowledge_boundary', 'hidden_information', 'forbidden_words'
]

export const UNIT_FIELDS = [
  'pov', 'pace', 'special_form', 'dialogue_constraints',
  'must_include', 'must_avoid'
]

// Field labels (Chinese)
export const FIELD_LABELS = {
  pov: '叙事视角', tense: '时态', pace: '节奏',
  language_density: '语言密度', tone: '情绪基调',
  dialogue_ratio: '对话占比', hard_bans: '禁用表达',
  terminology: '术语表',
  addressing: '称谓', sentence_length: '句长',
  favorite_words: '常用词', catchphrases: '口头禅',
  knowledge_boundary: '知识边界', hidden_information: '信息隐瞒',
  forbidden_words: '禁用词',
  special_form: '特殊文体', dialogue_constraints: '对话限制',
  must_include: '必须出现', must_avoid: '不得出现',
  platform_rules: '平台规则', compliance_rules: '合规禁区'
}
