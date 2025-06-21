import React from 'react';
import { Table } from 'react-bootstrap';
import { useNavigate } from 'react-router-dom';

/* eslint-disable @typescript-eslint/no-explicit-any */
export type TableData = {
  id: string | number;
  [key: string]: any;
};

interface CustomTableProps<T extends TableData> {
  data: T[];
  columns: {
    key: keyof T;
    header: string;
    render?: (item: T) => React.ReactNode;
    sortable?: boolean;
  }[];
  actions?: {
    name: string;
    action: (item: T) => void;
  }[];
  onSort?: (key: keyof T, order: 'asc' | 'desc') => void;
  currentSort?: { key: keyof T; direction: 'asc' | 'desc' };
}

function CustomTable<T extends TableData>({ data, columns, actions, onSort, currentSort }: CustomTableProps<T>) {
  const navigate = useNavigate();

  const handleAction = (action: string, item: T) => {
    switch (action) {
      case 'Edit':
        navigate(`${item.id}/edit`);
        break;
      case 'View':
        navigate(`${item.id}/view`);
        break;
      case 'Delete':
        navigate(`${item.id}/delete`);
        break;
      default:
        break;
      //const actionObj = actions?.find(a => a.name === action);
      /* if (actionObj) {
          actionObj.action(item);
        } */
    }
  };
  const handleSort = (key: keyof T) => {
    if (onSort) {
      const direction = currentSort && currentSort.key === key && currentSort.direction === 'asc' ? 'desc' : 'asc';
      onSort(key, direction);
    }
  };

  return (
    <Table hover responsive className='table-outline mb-0 d-none d-sm-table'>
      <thead className='thead-light'>
        <tr>
          {columns.map(column => (
            <th key={column.key.toString()} onClick={() => column.sortable && handleSort(column.key)}>
              {column.header}
              {column.sortable && currentSort && currentSort.key === column.key && <span>{currentSort.direction === 'asc' ? '▲' : '▼'}</span>}
            </th>
          ))}
          {actions && <th>Actions</th>}
        </tr>
      </thead>
      <tbody>
        {data.map(item => (
          <tr key={item.id}>
            {columns.map(column => (
              <td key={`${item.id}-${column.key as string}`}>{column.render ? column.render(item) : item[column.key]}</td>
            ))}
            {actions && (
              <td>
                {actions.map(action => (
                  <button key={action.name} onClick={() => handleAction(action.name, item)} className='btn btn-link'>
                    {action.name}
                  </button>
                ))}
              </td>
            )}
          </tr>
        ))}
      </tbody>
    </Table>
  );
}

export default CustomTable;
