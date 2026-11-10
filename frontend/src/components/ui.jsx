/* 작은 공용 UI 조각: PageHead · Card · Field · Select · Flow · HistoryTable · Pill */
export function PageHead({ kicker, title, sub, children }) {
  return <div className="head"><div><div className="eyebrow">{kicker}</div><h1>{title}</h1><p dangerouslySetInnerHTML={{ __html: sub || '' }} /></div><div className="actions">{children}</div></div>;
}
export const Card = ({ className = '', children, ...rest }) => <div className={`card ${className}`} {...rest}>{children}</div>;
export const Pill = ({ kind = '', children }) => <span className={`pill ${kind}`}>{children}</span>;
export function Field({ label, children }) { return <div className="field"><label>{label}</label>{children}</div>; }
export function Input({ label, ...props }) { return <div className="field"><label htmlFor={props.name}>{label}</label><input id={props.name} {...props} /></div>; }
export function Select({ label, options, ...props }) { return <div className="field"><label htmlFor={props.name}>{label}</label><select id={props.name} {...props}>{options.map(o => Array.isArray(o) ? <option key={o[0]} value={o[0]}>{o[1]}</option> : <option key={o} value={o}>{o}</option>)}</select></div>; }
export function Flow({ steps }) { return <div className="proc-flow">{steps.map((s, i) => <span key={i}><b>{i + 1}. {s[0]}</b>{s[1]}</span>)}</div>; }
export function HistoryTable({ items }) {
  if (!items.length) return <p className="muted">기록이 없습니다.</p>;
  return <div className="tablewrap"><table><thead><tr><th>일시</th><th>유형</th><th>내용</th></tr></thead><tbody>{items.map(h => <tr key={h.id}><td>{h.date}</td><td>{h.type}</td><td>{h.description}</td></tr>)}</tbody></table></div>;
}
export const Empty = ({ children }) => <div className="empty">{children}</div>;
