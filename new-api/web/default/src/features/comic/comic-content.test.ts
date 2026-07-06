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
import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import {
  COMIC_CHAPTER_KEYS,
  COMIC_FAQ_KEYS,
  COMIC_WORKBENCH_URL,
  HERO_CONTENT_KEYS,
  FINAL_CTA_KEYS,
} from './comic-content.ts'

describe('comic product content contract', () => {
  test('keeps the approved five-stage order via i18n keys', () => {
    assert.deepEqual(
      COMIC_CHAPTER_KEYS.map(({ id, titleKey }) => ({ id, titleKey })),
      [
        { id: 'story', titleKey: 'comic.chapter.story.title' },
        { id: 'storyboard', titleKey: 'comic.chapter.storyboard.title' },
        { id: 'canvas-production', titleKey: 'comic.chapter.canvas-production.title' },
        { id: 'director-quality', titleKey: 'comic.chapter.director-quality.title' },
        { id: 'asset-delivery', titleKey: 'comic.chapter.asset-delivery.title' },
      ],
    )
  })

  test('uses the approved workbench destination', () => {
    assert.equal(COMIC_WORKBENCH_URL, 'http://localhost:8080/home')
  })

  test('ships exactly five FAQ items with i18n keys', () => {
    assert.equal(COMIC_FAQ_KEYS.length, 5)
    assert.ok(COMIC_FAQ_KEYS.every((item) => item.questionKey && item.answerKey))
  })

  test('hero content uses i18n keys', () => {
    assert.ok(HERO_CONTENT_KEYS.eyebrowKey)
    assert.ok(HERO_CONTENT_KEYS.titleLine1Key)
    assert.ok(HERO_CONTENT_KEYS.titleHighlightKey)
    assert.ok(HERO_CONTENT_KEYS.leadKey)
    assert.ok(HERO_CONTENT_KEYS.primaryCtaKey)
    assert.ok(HERO_CONTENT_KEYS.secondaryCtaKey)
  })

  test('final CTA uses i18n keys', () => {
    assert.ok(FINAL_CTA_KEYS.titleKey)
    assert.ok(FINAL_CTA_KEYS.subtitleKey)
    assert.ok(FINAL_CTA_KEYS.ctaKey)
  })
})
