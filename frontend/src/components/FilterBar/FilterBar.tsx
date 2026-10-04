import { useId } from "react";
import { Icon } from "../Icon/Icon";

export interface FilterOption {
  value: string;
  label: string;
}

interface FilterBarProps {
  search: string;
  onSearch: (value: string) => void;
  searchLabel: string;
  filterLabel: string;
  filterValue: string;
  filterOptions: FilterOption[];
  onFilter: (value: string) => void;
  /** Shown only while a search or filter is active. */
  active: boolean;
  onReset: () => void;
  resetLabel: string;
}

/** C-28: search plus one filter; the state lives with the caller (the URL). The native select opens as a sheet on phones. */
export function FilterBar(props: FilterBarProps) {
  const selectId = useId();
  return (
    <div className="row g-2 align-items-end mb-3">
      <div className="col-12 col-md-5">
        <div className="input-icon">
          <span className="input-icon-addon">
            <Icon name="search" size={18} />
          </span>
          <input
            type="search"
            className="form-control"
            placeholder={props.searchLabel}
            aria-label={props.searchLabel}
            value={props.search}
            onChange={(e) => props.onSearch(e.target.value)}
          />
        </div>
      </div>
      <div className="col-6 col-md-3">
        <label className="form-label mb-1" htmlFor={selectId}>
          {props.filterLabel}
        </label>
        <select
          id={selectId}
          className="form-select"
          value={props.filterValue}
          onChange={(e) => props.onFilter(e.target.value)}
        >
          {props.filterOptions.map((o) => (
            <option key={o.value} value={o.value}>
              {o.label}
            </option>
          ))}
        </select>
      </div>
      {props.active ? (
        <div className="col-auto">
          <button type="button" className="btn btn-link" onClick={props.onReset}>
            {props.resetLabel}
          </button>
        </div>
      ) : null}
    </div>
  );
}
