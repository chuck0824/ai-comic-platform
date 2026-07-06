/*
Copyright (C) 2023-2026 QuantumNous

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as
published by the Free Software Foundation, either version 3 of the
License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program. If not, see <https://www.gnu.org/licenses/>.

For commercial licensing, please contact support@quantumnous.com
*/
import { useState } from 'react'

const NODES = ['角色参考', '分镜节点', '场景参考', '提示词合成', '视频生成'] as const
const NODE_SUBTITLES = [
  '林默 · 正面定妆',
  '06-12 · 雨中近景',
  '旧城夜街 · 雨',
  '角色 + 场景 + 运镜',
  'Seedance · V4',
]
const TOOLS = ['↖', 'T', '▧', '▶', '♙', '◇']
const TASKS: [string, string][] = [
  ['06-12 生成中', 'running'],
  ['06-11 已完成', 'done'],
  ['06-13 等待中', 'waiting'],
  ['06-10 可重试', 'failed'],
]

export function CanvasProductionConcept() {
  const [selected, setSelected] = useState<(typeof NODES)[number]>('角色参考')

  return (
    <div className="comic-concept-window comic-canvas-ui">
      <header>
        <strong>第 06 集生产画布</strong>
        <span>● 已保存　Workspace 资产　分镜表　批量生成</span>
      </header>
      <div className="comic-canvas-grid">
        <aside aria-label="画布工具">
          {TOOLS.map((tool) => (
            <button type="button" key={tool}>{tool}</button>
          ))}
        </aside>
        <main className="comic-concept-canvas">
          {NODES.map((node, index) => (
            <button
              type="button"
              key={node}
              className={`comic-node comic-node-${index + 1}`}
              aria-pressed={selected === node}
              onClick={() => setSelected(node)}
            >
              <b>{node}</b>
              <span>{NODE_SUBTITLES[index]}</span>
            </button>
          ))}
        </main>
        <aside>
          <p className="comic-concept-label">节点与任务</p>
          <h3>{selected}</h3>
          <button type="button">保存为资产</button>
          {TASKS.map(([label, status]) => (
            <p key={label} data-status={status}>{label}</p>
          ))}
        </aside>
      </div>
    </div>
  )
}
