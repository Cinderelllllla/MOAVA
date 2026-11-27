/* 전국 시/도 → 시/군/구 → 읍/면/동 3단계 연동 드롭다운
 * - mode="required": 회원가입 · 내 정보 수정 · 모임 만들기 (3단계 필수 선택)
 * - mode="filter":   지역 계모임 찾기 지도 필터 (단계별 '전체' 옵션 지원)
 */
import { useMemo } from 'react';
import {
  getDongOptions,
  getSidoOptions,
  getSigunguOptions,
  normalizeSidoName,
} from '../common/regions';

const ALL = '전체';

export default function RegionSelector({
  sido = '',
  sigungu = '',
  dong = '',
  onChange,
  mode = 'required',
  namePrefix = 'region',
  labels,
  disabled = false,
  className = '',
}) {
  const isFilter = mode === 'filter';
  const text = {
    sido: labels?.sido ?? (isFilter ? '시/도' : '활동 지역 · 시/도'),
    sigungu: labels?.sigungu ?? (isFilter ? '시/군/구' : '활동 지역 · 시/군/구'),
    dong: labels?.dong ?? (isFilter ? '읍/면/동' : '활동 지역 · 읍/면/동'),
  };

  const sidoOptions = useMemo(() => getSidoOptions(), []);

  const normSido = useMemo(() => {
    if (!sido || sido === ALL) return isFilter ? ALL : '';
    const normalized = normalizeSidoName(sido);
    return sidoOptions.includes(normalized) ? normalized : isFilter ? ALL : '';
  }, [sido, sidoOptions, isFilter]);

  const sigunguOptions = useMemo(
    () => (normSido && normSido !== ALL ? getSigunguOptions(normSido) : []),
    [normSido]
  );

  const normSigungu = useMemo(() => {
    if (!sigungu || sigungu === ALL) return isFilter ? ALL : '';
    return sigunguOptions.includes(sigungu) ? sigungu : isFilter ? ALL : '';
  }, [sigungu, sigunguOptions, isFilter]);

  const dongOptions = useMemo(
    () =>
      normSido && normSido !== ALL && normSigungu && normSigungu !== ALL
        ? getDongOptions(normSido, normSigungu)
        : [],
    [normSido, normSigungu]
  );

  const normDong = useMemo(() => {
    if (!dong || dong === ALL) return isFilter ? ALL : '';
    return dongOptions.includes(dong) ? dong : isFilter ? ALL : '';
  }, [dong, dongOptions, isFilter]);

  const handleSidoChange = (e) => {
    const nextSido = e.target.value;
    if (isFilter) {
      if (!nextSido || nextSido === ALL) {
        onChange?.({ sido: ALL, sigungu: ALL, dong: ALL });
        return;
      }
      const nextSgList = getSigunguOptions(nextSido);
      const autoSg = nextSgList.length === 1 ? nextSgList[0] : ALL;
      onChange?.({ sido: nextSido, sigungu: autoSg, dong: ALL });
      return;
    }

    const nextSgList = nextSido ? getSigunguOptions(nextSido) : [];
    const autoSg = nextSgList.length === 1 ? nextSgList[0] : '';
    onChange?.({ sido: nextSido, sigungu: autoSg, dong: '' });
  };

  const handleSigunguChange = (e) => {
    const nextSg = e.target.value;
    if (isFilter) {
      onChange?.({
        sido: normSido || ALL,
        sigungu: nextSg || ALL,
        dong: ALL,
      });
      return;
    }
    onChange?.({
      sido: normSido,
      sigungu: nextSg,
      dong: '',
    });
  };

  const handleDongChange = (e) => {
    const nextDong = e.target.value;
    onChange?.({
      sido: normSido || (isFilter ? ALL : ''),
      sigungu: normSigungu || (isFilter ? ALL : ''),
      dong: nextDong || (isFilter ? ALL : ''),
    });
  };

  const sgDisabled = disabled || !normSido || normSido === ALL;
  const dongDisabled = disabled || sgDisabled || !normSigungu || normSigungu === ALL;

  const fields = (
    <>
      <div className="field region-field-sido">
        <label htmlFor={`${namePrefix}Sido`}>{text.sido}</label>
        <select
          id={`${namePrefix}Sido`}
          name={`${namePrefix}Sido`}
          value={normSido}
          onChange={handleSidoChange}
          required={!isFilter}
          disabled={disabled}
        >
          <option value={isFilter ? ALL : ''}>
            {isFilter ? '전체 시/도' : '시/도 선택'}
          </option>
          {sidoOptions.map((opt) => (
            <option key={opt} value={opt}>
              {opt}
            </option>
          ))}
        </select>
      </div>

      <div className="field region-field-sigungu">
        <label htmlFor={`${namePrefix}Sigungu`}>{text.sigungu}</label>
        <select
          id={`${namePrefix}Sigungu`}
          name={`${namePrefix}Sigungu`}
          value={normSigungu}
          onChange={handleSigunguChange}
          required={!isFilter}
          disabled={sgDisabled}
        >
          <option value={isFilter ? ALL : ''}>
            {sgDisabled
              ? isFilter
                ? '시/도 먼저 선택'
                : '시/도를 먼저 선택하세요'
              : isFilter
                ? '전체 시/군/구'
                : '시/군/구 선택'}
          </option>
          {sigunguOptions.map((opt) => (
            <option key={opt} value={opt}>
              {opt}
            </option>
          ))}
        </select>
      </div>

      <div className="field region-field-dong">
        <label htmlFor={`${namePrefix}Dong`}>{text.dong}</label>
        <select
          id={`${namePrefix}Dong`}
          name={`${namePrefix}Dong`}
          value={normDong}
          onChange={handleDongChange}
          required={!isFilter}
          disabled={dongDisabled}
        >
          <option value={isFilter ? ALL : ''}>
            {dongDisabled
              ? isFilter
                ? '시/군/구 먼저 선택'
                : '시/군/구를 먼저 선택하세요'
              : isFilter
                ? '전체 읍/면/동'
                : '읍/면/동 선택'}
          </option>
          {dongOptions.map((opt) => (
            <option key={opt} value={opt}>
              {opt}
            </option>
          ))}
        </select>
      </div>
    </>
  );

  if (className) {
    return <div className={className}>{fields}</div>;
  }
  return fields;
}
