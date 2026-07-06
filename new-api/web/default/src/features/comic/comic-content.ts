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
export const COMIC_WORKBENCH_URL = 'http://localhost:8080/home'

export type ComicChapterId =
  | 'story'
  | 'storyboard'
  | 'canvas-production'
  | 'director-quality'
  | 'asset-delivery'

/** Structure-only chapter definition — all text resolved via i18n keys at render time. */
export type ComicChapterKey = {
  id: ComicChapterId
  index: string
  eyebrowKey: string
  titleKey: string
  descriptionKey: string
  capabilityKeys: readonly string[]
  ctaKey: string
  tone: 'cyan' | 'violet' | 'blue' | 'pink' | 'green'
}

export const COMIC_CHAPTER_KEYS: readonly ComicChapterKey[] = [
  {
    id: 'story', index: '01',
    eyebrowKey: 'comic.chapter.story.eyebrow',
    titleKey: 'comic.chapter.story.title',
    descriptionKey: 'comic.chapter.story.description',
    capabilityKeys: [
      'comic.chapter.story.capability.seed',
      'comic.chapter.story.capability.profile',
      'comic.chapter.story.capability.worldview',
      'comic.chapter.story.capability.bible',
    ],
    ctaKey: 'comic.chapter.story.cta',
    tone: 'cyan',
  },
  {
    id: 'storyboard', index: '02',
    eyebrowKey: 'comic.chapter.storyboard.eyebrow',
    titleKey: 'comic.chapter.storyboard.title',
    descriptionKey: 'comic.chapter.storyboard.description',
    capabilityKeys: [
      'comic.chapter.storyboard.capability.outline',
      'comic.chapter.storyboard.capability.shotEdit',
      'comic.chapter.storyboard.capability.promptTemplate',
      'comic.chapter.storyboard.capability.versionReview',
    ],
    ctaKey: 'comic.chapter.storyboard.cta',
    tone: 'violet',
  },
  {
    id: 'canvas-production', index: '03',
    eyebrowKey: 'comic.chapter.canvas-production.eyebrow',
    titleKey: 'comic.chapter.canvas-production.title',
    descriptionKey: 'comic.chapter.canvas-production.description',
    capabilityKeys: [
      'comic.chapter.canvas-production.capability.nodeCanvas',
      'comic.chapter.canvas-production.capability.refAssets',
      'comic.chapter.canvas-production.capability.promptCompose',
      'comic.chapter.canvas-production.capability.taskQueue',
    ],
    ctaKey: 'comic.chapter.canvas-production.cta',
    tone: 'blue',
  },
  {
    id: 'director-quality', index: '04',
    eyebrowKey: 'comic.chapter.director-quality.eyebrow',
    titleKey: 'comic.chapter.director-quality.title',
    descriptionKey: 'comic.chapter.director-quality.description',
    capabilityKeys: [
      'comic.chapter.director-quality.capability.sceneTree',
      'comic.chapter.director-quality.capability.viewport3d',
      'comic.chapter.director-quality.capability.timeline',
      'comic.chapter.director-quality.capability.verifyFreeze',
    ],
    ctaKey: 'comic.chapter.director-quality.cta',
    tone: 'pink',
  },
  {
    id: 'asset-delivery', index: '05',
    eyebrowKey: 'comic.chapter.asset-delivery.eyebrow',
    titleKey: 'comic.chapter.asset-delivery.title',
    descriptionKey: 'comic.chapter.asset-delivery.description',
    capabilityKeys: [
      'comic.chapter.asset-delivery.capability.assetHistory',
      'comic.chapter.asset-delivery.capability.versionTracking',
      'comic.chapter.asset-delivery.capability.qualityReport',
      'comic.chapter.asset-delivery.capability.deliveryManifest',
    ],
    ctaKey: 'comic.chapter.asset-delivery.cta',
    tone: 'green',
  },
]

export type ComicFaqKey = { questionKey: string; answerKey: string }

export const COMIC_FAQ_KEYS: readonly ComicFaqKey[] = [
  { questionKey: 'comic.faq.q1', answerKey: 'comic.faq.a1' },
  { questionKey: 'comic.faq.q2', answerKey: 'comic.faq.a2' },
  { questionKey: 'comic.faq.q3', answerKey: 'comic.faq.a3' },
  { questionKey: 'comic.faq.q4', answerKey: 'comic.faq.a4' },
  { questionKey: 'comic.faq.q5', answerKey: 'comic.faq.a5' },
]

export const HERO_CONTENT_KEYS = {
  eyebrowKey: 'comic.hero.eyebrow',
  titleLine1Key: 'comic.hero.titleLine1',
  titleHighlightKey: 'comic.hero.titleHighlight',
  leadKey: 'comic.hero.lead',
  primaryCtaKey: 'comic.hero.primaryCta',
  secondaryCtaKey: 'comic.hero.secondaryCta',
  proof1Key: 'comic.hero.proof1',
  proof2Key: 'comic.hero.proof2',
  proof3Key: 'comic.hero.proof3',
} as const

export const FINAL_CTA_KEYS = {
  titleKey: 'comic.finalCta.title',
  subtitleKey: 'comic.finalCta.subtitle',
  ctaKey: 'comic.finalCta.cta',
} as const
