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

const ASSETS = [
  '林默 · 角色定妆',
  '旧城街道 · 夜',
  '06-12 · 采用版本',
  '雨景特效 · V3',
  '第 06 集 · 成片',
  '字幕与封面',
] as const

export function AssetDeliveryConcept() {
  const [selected, setSelected] = useState<(typeof ASSETS)[number]>(ASSETS[0])
  const [message, setMessage] = useState('')

  return (
    <div className="comic-concept-window comic-assets-ui">
      <header>
        <strong>资产生成历史</strong>
        <span>搜索资产…　分类：全部　集合：全部资产</span>
      </header>
      <div className="comic-assets-grid">
        <main>
          <div className="comic-asset-cards">
            {ASSETS.map((asset, index) => (
              <button
                type="button"
                key={asset}
                aria-pressed={selected === asset}
                onClick={() => setSelected(asset)}
              >
                <i data-variant={index % 3} />
                <span>{asset}</span>
              </button>
            ))}
          </div>
        </main>
        <aside>
          <p className="comic-concept-label">资产详情</p>
          <h3>{selected}</h3>
          <p>状态 <em>已完成</em></p>
          <p>采用版本 <em>V4</em></p>
          <button type="button">☆ 收藏</button>
          <button type="button">↓ 下载</button>
          <p className="comic-concept-label">质量与交付</p>
          <p>成片文件 <em>24/24</em></p>
          <p>字幕文件 <em>24/24</em></p>
          <p>质量报告 <em>通过</em></p>
          <p>版本记录 <em>完整</em></p>
          <button type="button" onClick={() => setMessage('交付清单已准备')}>
            导出交付清单
          </button>
        </aside>
      </div>
      <span className="comic-live" aria-live="polite">{message}</span>
    </div>
  )
}
