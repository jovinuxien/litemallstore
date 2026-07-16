import * as React from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useAppDispatch, useAppSelector } from 'app/config/store';
import { addVisitedTag, removeVisitedTag } from 'app/shared/reducers/private/adminUiSlice';
import { titleForPath } from './menu.config';

// Tags-view bar (mirrors upstream TagsView): one tab per visited admin route.
// Click to navigate, × to close (Dashboard is affixed and cannot be closed).
const TagsView: React.FC = () => {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const location = useLocation();
  const tags = useAppSelector(state => state.adminUi.visitedTags);

  // record the current route as a tag whenever it changes
  React.useEffect(() => {
    const title = titleForPath(location.pathname);
    if (title) {
      dispatch(addVisitedTag({ path: location.pathname, title }));
    }
  }, [dispatch, location.pathname]);

  const onClose = (e: React.MouseEvent, path: string) => {
    e.stopPropagation();
    dispatch(removeVisitedTag(path));
    // Home is realm-relative: affiliate tabs fall back to the affiliate
    // dashboard, admin tabs to the admin one.
    if (location.pathname === path) navigate(path.startsWith('/affiliate') ? '/affiliate/dashboard' : '/admin/dashboard');
  };

  return (
    <div className='tags-view'>
      {tags.map(tag => {
        const active = location.pathname === tag.path;
        return (
          <a
            key={tag.path}
            className={`tags-view-item${active ? ' active' : ''}`}
            onClick={() => navigate(tag.path)}
            role='button'
          >
            <span className='tags-dot' />
            {tag.title}
            {!tag.affix && (
              <i className='tags-close' onClick={e => onClose(e, tag.path)}>
                ×
              </i>
            )}
          </a>
        );
      })}
    </div>
  );
};

export default TagsView;
