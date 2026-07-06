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
import { useComicContent } from '../use-comic-content'

export function ComicFaq() {
  const { faqs } = useComicContent()

  return (
    <section className="comic-faq" aria-labelledby="comic-faq-title">
      <div className="comic-section-heading">
        <p>FAQ</p>
        <h2 id="comic-faq-title">开始之前，你可能想知道</h2>
      </div>
      <div className="comic-faq-list">
        {faqs.map(({ question, answer }) => (
          <details key={question}>
            <summary>{question}</summary>
            <p>{answer}</p>
          </details>
        ))}
      </div>
    </section>
  )
}
