export const CREATION_METHODS = [
  { key: 'quick', label: 'AI 快速创作', desc: '输入灵感，AI 快速生成剧本', icon: 'Lightning', route: '/script-gen/new?mode=quick' },
  { key: 'professional', label: 'AI 专业创作', desc: '分步骤精细创作，逐集打磨', icon: 'EditPen', route: '/script-gen/new?mode=professional' },
  { key: 'upload', label: '上传已有文稿', desc: '上传 TXT/DOCX，自动解析分集', icon: 'Upload', route: '/script-gen/new?mode=upload' },
  { key: 'tvc', label: 'TVC 创作', desc: '商业广告脚本创作工具', icon: 'VideoCamera', route: '/script-gen/new?mode=tvc' }
]

export function createLaunchpadViewModel({ recent = [], todos = [] }) {
  return {
    methods: CREATION_METHODS,
    recent: recent.slice(0, 5),
    todos
  }
}
