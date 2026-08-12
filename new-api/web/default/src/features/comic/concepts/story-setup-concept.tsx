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

const STAGES = [
  '故事种子', '角色设定', '故事梗概', '分集大纲',
  '单集正文', '剧本审核', '制作分镜',
] as const

export function StorySetupConcept() {
  const [stage, setStage] = useState<(typeof STAGES)[number]>('角色设定')
  const [message, setMessage] = useState('')

  const handleAccept = () => {
    setMessage('已采用为当前版本')
    window.setTimeout(() => setMessage(''), 1600)
  }

  return (
    <div className="comic-concept-window comic-story-ui">
      <header>
        <strong>《雨夜重逢》</strong>
        <span>● 已自动保存</span>
      </header>
      <div className="comic-concept-grid comic-story-grid">
        <aside>
          <p className="comic-concept-label">创作流程 · 42%</p>
          {STAGES.map((item, index) => (
            <button
              type="button"
              key={item}
              aria-pressed={stage === item}
              onClick={() => setStage(item)}
            >
              {index === 0 ? '✓ ' : ''}{item}
            </button>
          ))}
        </aside>
        <main>
          <p className="comic-concept-label">当前阶段</p>
          <section className="comic-concept-card">
            <h3>{stage}</h3>
            <p>AI 将根据故事种子生成内容，并保留可采用版本。</p>
            <div className="comic-concept-copy">
              <b>林默｜男，29 岁｜调查记者</b>
              <br />
              外冷内热，擅长从细节中发现矛盾。雨夜回到旧城调查一份被篡改的账本。
            </div>
            <button type="button" onClick={handleAccept}>采用此版本</button>
          </section>
          <section className="comic-concept-card">
            <p>版本历史</p>
            <span>v4 · AI 生成</span> <span>v3 · 手动</span>
          </section>
        </main>
        <aside>
          <p className="comic-concept-label">项目上下文</p>
          <section className="comic-concept-card">
            <b>创作圣经</b>
            <p>版本 v3</p>
            <p>健康度 92%</p>
            <p>锁定事实 18</p>
          </section>
          <section className="comic-concept-card">
            <b>影响范围</b>
            <p>修改主角身份将影响 6 集、42 个镜头。</p>
          </section>
        </aside>
      </div>
      <p className="comic-live" aria-live="polite">{message}</p>
    </div>
  )
}
