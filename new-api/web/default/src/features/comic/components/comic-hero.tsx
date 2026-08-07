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
import { ArrowDown, ArrowRight } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { openAicpWorkbenchWithSso } from '@/lib/aicp-sso'
import { useComicContent } from '../use-comic-content'

export function ComicHero() {
  const { hero } = useComicContent()

  return (
    <section id="comic-top" data-comic-section className="comic-hero">
      <div aria-hidden className="comic-hero-aurora" />
      <div className="comic-hero-grid">
        <div>
          <p className="comic-eyebrow">{hero.eyebrow}</p>
          <h1>{hero.titleLine1}<br /><span>{hero.titleHighlight}</span></h1>
          <p className="comic-hero-lead">{hero.lead}</p>
          <div className="comic-hero-actions">
            <Button type="button" onClick={() => void openAicpWorkbenchWithSso('/home')}>
              {hero.primaryCta}<ArrowRight aria-hidden />
            </Button>
            <Button variant="outline" render={<a href="#story" />}>{hero.secondaryCta}<ArrowDown aria-hidden /></Button>
          </div>
          <ul className="comic-proof-list">
            <li>{hero.proof1}</li><li>{hero.proof2}</li><li>{hero.proof3}</li>
          </ul>
        </div>
        {/* Decorative only — hidden from screen readers per spec §13.3 */}
        <div className="comic-hero-window" aria-hidden="true">
          <div className="comic-window-bar"><i /><i /><i /><span>《雨夜重逢》 · 第 06 集</span></div>
          <div className="comic-hero-canvas">
            <div className="comic-hero-rail"><i className="active" /><i /><i /><i /></div>
            <div className="comic-node-map">
              <span className="node node-character">角色设定<b>林默</b></span>
              <span className="node node-shot">分镜 06-12<b>雨中近景</b></span>
              <span className="node node-generate">视频生成<b>渲染中 84%</b></span>
              <span className="node node-quality">质量检查<b>已通过</b></span>
              <span className="node node-delivery">交付版本<b>V12</b></span>
            </div>
          </div>
        </div>
      </div>
    </section>
  )
}
