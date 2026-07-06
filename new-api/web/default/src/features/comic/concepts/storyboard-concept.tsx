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

const SHOTS = ['03-01', '03-02', '03-03', '03-04', '03-05', '03-06'] as const
const TABS = ['镜头编辑', '情绪节奏', '提示词模板', '创作规则', '角色视觉', '版本与审核']

export function StoryboardConcept() {
  const [selected, setSelected] = useState<(typeof SHOTS)[number]>('03-01')

  return (
    <div className="comic-concept-window comic-storyboard-ui">
      <header>
        <strong>《雨夜重逢》· 分镜专业编辑器</strong>
        <span>V8 · 草稿　已保存</span>
      </header>
      <nav aria-label="分镜模块">
        {TABS.map((tab, index) => (
          <button type="button" key={tab} aria-current={index === 0 ? 'page' : undefined}>
            {tab}
          </button>
        ))}
      </nav>
      <div className="comic-concept-grid comic-storyboard-grid">
        <aside>
          <p className="comic-concept-label">场景导航</p>
          <button type="button" aria-pressed="true">场景 01 · 雨夜旧城</button>
          <button type="button">场景 02 · 便利店</button>
          <button type="button">场景 03 · 天台</button>
        </aside>
        <main className="comic-shot-grid">
          {SHOTS.map((shot, index) => (
            <button
              type="button"
              key={shot}
              aria-pressed={selected === shot}
              onClick={() => setSelected(shot)}
            >
              <i data-variant={index % 3} />
              <span>{shot} · 镜头画面</span>
            </button>
          ))}
        </main>
        <aside>
          <p className="comic-concept-label">镜头检查器</p>
          <h3>镜头 {selected}</h3>
          <label>
            景别 / 机位
            <input readOnly value="远景 · 平视" />
          </label>
          <label>
            角色动作
            <input readOnly value="林默撑伞穿过街口" />
          </label>
          <label>
            图片提示词
            <textarea readOnly value="雨夜旧城，霓虹反射，电影构图…" />
          </label>
          <label>
            视频动作提示词
            <input readOnly value="镜头缓慢向前推进" />
          </label>
        </aside>
      </div>
    </div>
  )
}
