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

const OBJECTS = ['林默', '主相机', '路灯', '街道'] as const

export function DirectorQualityConcept() {
  const [selected, setSelected] = useState<(typeof OBJECTS)[number]>('林默')
  const [playing, setPlaying] = useState(false)
  const [status, setStatus] = useState('未保存')
  const [message, setMessage] = useState('')

  const handleVerify = () => setMessage('校验通过，可冻结版本')
  const handleFreeze = () => {
    setStatus('冻结版本 #12')
    setMessage('Revision #12 已冻结')
  }

  return (
    <div className="comic-concept-window comic-director-ui">
      <header>
        <span>← 返回 Canvas</span>
        <strong>SHOT · 24fps · 8000ms</strong>
        <span>{status}</span>
        <button type="button" onClick={handleVerify}>校验</button>
        <button type="button" onClick={handleFreeze}>冻结 Revision</button>
      </header>
      <div className="comic-director-grid">
        <aside>
          <p className="comic-concept-label">场景树</p>
          {OBJECTS.map((item) => (
            <button
              type="button"
              key={item}
              aria-pressed={selected === item}
              onClick={() => setSelected(item)}
            >
              {item}
            </button>
          ))}
        </aside>
        <main className="comic-viewport">
          <i className="comic-floor" />
          <i className="comic-human" />
          <i className="comic-camera" />
          <i className="comic-light" />
          <i className="comic-gizmo" />
          <span aria-live="polite">{message}</span>
        </main>
        <aside>
          <p className="comic-concept-label">属性</p>
          <h3>{selected}</h3>
          <label>
            位置 X / Y / Z
            <input readOnly value="0.0 / 1.2 / 0.0" />
          </label>
          <label>
            动作片段
            <input readOnly value="turn_back_01 · 2.0s" />
          </label>
        </aside>
      </div>
      <footer className="comic-timeline">
        <button
          type="button"
          onClick={() => setPlaying((v) => !v)}
          aria-label={playing ? '暂停' : '播放'}
        >
          {playing ? 'Ⅱ' : '▶'}
        </button>
        <span>{playing ? '3.1s' : '0.0s'} / 8.0s</span>
        <i><b /></i>
        <span>24fps</span>
      </footer>
    </div>
  )
}
